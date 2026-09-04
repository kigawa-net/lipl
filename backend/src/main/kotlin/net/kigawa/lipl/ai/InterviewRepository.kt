package net.kigawa.lipl.ai

import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

class InterviewLimitExceededException :
    Exception("AI質問の上限に達しました。LPを生成してください")

class InterviewRepository(private val claudeClient: ClaudeClient) {

    fun getState(storeId: Long): InterviewStateResponse = transaction { toState(loadMessages(storeId)) }

    // userMessage が null の場合は最初の質問を生成する（既に開始済みなら現在の状態を返すのみ）。
    // それ以外は回答を保存し、質問回数が上限未満であれば次の質問を生成する。
    suspend fun sendMessage(storeId: Long, storeContext: String, userMessage: String?): InterviewStateResponse {
        val current = transaction { loadMessages(storeId) }

        if (userMessage == null) {
            if (current.isNotEmpty()) return toState(current)
            val question = claudeClient.complete(
                INTERVIEW_SYSTEM_PROMPT,
                listOf(ClaudeMessage("user", storeContext)),
            )
            return toState(transaction { insertMessage(storeId, "assistant", question); loadMessages(storeId) })
        }

        val answeredCount = current.count { it.role == "user" }
        if (answeredCount >= FREE_PLAN_INTERVIEW_QUESTION_LIMIT) {
            throw InterviewLimitExceededException()
        }

        val afterAnswer = transaction { insertMessage(storeId, "user", userMessage); loadMessages(storeId) }
        if (answeredCount + 1 >= FREE_PLAN_INTERVIEW_QUESTION_LIMIT) {
            return toState(afterAnswer)
        }

        val history = listOf(ClaudeMessage("user", storeContext)) + afterAnswer.map {
            ClaudeMessage(it.role, it.content)
        }
        val nextQuestion = claudeClient.complete(INTERVIEW_SYSTEM_PROMPT, history)
        return toState(transaction { insertMessage(storeId, "assistant", nextQuestion); loadMessages(storeId) })
    }

    // LP生成時に呼び出し、次回ヒアリングを新しいセッションとして開始できるようにする。
    fun reset(storeId: Long) = transaction {
        InterviewMessagesTable.deleteWhere { InterviewMessagesTable.storeId eq storeId }
    }

    private fun insertMessage(storeId: Long, role: String, content: String) {
        InterviewMessagesTable.insert {
            it[InterviewMessagesTable.storeId] = storeId
            it[InterviewMessagesTable.role] = role
            it[InterviewMessagesTable.content] = content
        }
    }

    private fun loadMessages(storeId: Long): List<InterviewMessageResponse> =
        InterviewMessagesTable.selectAll()
            .andWhere { InterviewMessagesTable.storeId eq storeId }
            .orderBy(InterviewMessagesTable.id to SortOrder.ASC)
            .map { it.toResponse() }

    private fun ResultRow.toResponse(): InterviewMessageResponse = InterviewMessageResponse(
        id = this[InterviewMessagesTable.id],
        role = this[InterviewMessagesTable.role],
        content = this[InterviewMessagesTable.content],
    )

    private fun toState(messages: List<InterviewMessageResponse>): InterviewStateResponse {
        val questionCount = messages.count { it.role == "assistant" }
        val answeredCount = messages.count { it.role == "user" }
        return InterviewStateResponse(
            messages = messages,
            questionCount = questionCount,
            questionLimit = FREE_PLAN_INTERVIEW_QUESTION_LIMIT,
            limitReached = answeredCount >= FREE_PLAN_INTERVIEW_QUESTION_LIMIT,
        )
    }
}
