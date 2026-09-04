package net.kigawa.lipl.ai

// Anthropicの静的APIキーは使わず、Workload Identity Federationで短命トークンに
// 交換して使う。KeycloakのクライアントクレデンシャルでJWTを取得し、それを
// Anthropicの短命アクセストークンと交換する（ClaudeClient参照）。
data class AnthropicFederationConfig(
    val keycloakTokenUrl: String,
    val keycloakClientId: String,
    val keycloakClientSecret: String,
    val federationRuleId: String,
    val organizationId: String,
    val serviceAccountId: String,
    val workspaceId: String?,
)

data class ClaudeConfig(
    val model: String,
    val federation: AnthropicFederationConfig,
)

// Freeプランでは軽量なClaude Haikuを使用する（Basic/ProのSonnet切り替えは課金プラン実装時に対応）。
fun claudeConfigFromEnv(): ClaudeConfig {
    val keycloakIssuer = System.getenv("KEYCLOAK_ISSUER")
        ?: error("環境変数 KEYCLOAK_ISSUER が設定されていません")

    return ClaudeConfig(
        model = System.getenv("ANTHROPIC_MODEL") ?: "claude-3-5-haiku-20241022",
        federation = AnthropicFederationConfig(
            keycloakTokenUrl = "$keycloakIssuer/protocol/openid-connect/token",
            keycloakClientId = System.getenv("ANTHROPIC_KEYCLOAK_CLIENT_ID")
                ?: error("環境変数 ANTHROPIC_KEYCLOAK_CLIENT_ID が設定されていません"),
            keycloakClientSecret = System.getenv("ANTHROPIC_KEYCLOAK_CLIENT_SECRET")
                ?: error("環境変数 ANTHROPIC_KEYCLOAK_CLIENT_SECRET が設定されていません"),
            federationRuleId = System.getenv("ANTHROPIC_FEDERATION_RULE_ID")
                ?: error("環境変数 ANTHROPIC_FEDERATION_RULE_ID が設定されていません"),
            organizationId = System.getenv("ANTHROPIC_ORGANIZATION_ID")
                ?: error("環境変数 ANTHROPIC_ORGANIZATION_ID が設定されていません"),
            serviceAccountId = System.getenv("ANTHROPIC_SERVICE_ACCOUNT_ID")
                ?: error("環境変数 ANTHROPIC_SERVICE_ACCOUNT_ID が設定されていません"),
            workspaceId = System.getenv("ANTHROPIC_WORKSPACE_ID"),
        ),
    )
}
