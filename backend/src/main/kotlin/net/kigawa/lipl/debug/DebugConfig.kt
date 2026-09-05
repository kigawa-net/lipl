package net.kigawa.lipl.debug

data class DebugConfig(val debugMenuEnabled: Boolean)

// 未設定時はfalse（本番でうっかり有効化されることを防ぐ安全側のデフォルト）。
fun debugConfigFromEnv(): DebugConfig = DebugConfig(
    debugMenuEnabled = System.getenv("DEBUG_MENU_ENABLED")?.toBoolean() ?: false,
)
