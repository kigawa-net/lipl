package net.kigawa.lipl.ai

data class ClaudeConfig(
    val apiKey: String,
    val model: String,
)

// Freeプランでは軽量なClaude Haikuを使用する（Basic/ProのSonnet切り替えは課金プラン実装時に対応）。
fun claudeConfigFromEnv(): ClaudeConfig = ClaudeConfig(
    apiKey = System.getenv("ANTHROPIC_API_KEY")
        ?: error("環境変数 ANTHROPIC_API_KEY が設定されていません"),
    model = System.getenv("ANTHROPIC_MODEL") ?: "claude-3-5-haiku-20241022",
)
