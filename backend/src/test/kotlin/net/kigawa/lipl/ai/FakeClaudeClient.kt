package net.kigawa.lipl.ai

// テストでは実際のClaude APIを呼び出さず、あらかじめ用意した応答を順番に返す。
class FakeClaudeClient(private val responses: List<String>) : ClaudeClient {
    var callCount = 0
        private set

    override suspend fun complete(systemPrompt: String, messages: List<ClaudeMessage>): String {
        val response = responses.getOrElse(callCount) { responses.last() }
        callCount++
        return response
    }
}
