package net.kigawa.lipl.kaft

data class KaftConfig(
    // クラスタ内部からkaftのinternal APIを呼び出す際のURL（サーバー間通信用）。
    val baseUrl: String,
    // ブラウザがkaftのfiles APIに直接アップロード/取得する際のURL（公開Ingress経由）。
    val publicBaseUrl: String,
    val internalJwtSecret: String,
)

fun kaftConfigFromEnv(): KaftConfig = KaftConfig(
    baseUrl = System.getenv("KAFT_BASE_URL")
        ?: error("環境変数 KAFT_BASE_URL が設定されていません"),
    publicBaseUrl = System.getenv("KAFT_PUBLIC_BASE_URL")
        ?: error("環境変数 KAFT_PUBLIC_BASE_URL が設定されていません"),
    internalJwtSecret = System.getenv("KAFT_INTERNAL_JWT_SECRET")
        ?: error("環境変数 KAFT_INTERNAL_JWT_SECRET が設定されていません"),
)
