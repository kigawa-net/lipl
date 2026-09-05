package net.kigawa.lipl.ai

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

class LpContentNotFoundException : Exception("LPがまだ生成されていません")

class LpGenerationLimitExceededException :
    Exception("AI生成の上限に達しました。プランのアップグレードが必要です")

@Serializable
private data class GenerationResult(val catchphrase: String, val pageHtml: String)

class LpRepository(
    private val claudeClient: ClaudeClient,
    private val interviewRepository: InterviewRepository,
) {

    // デバッグメニュー（stg等の検証環境限定）用。アカウント単位のAI生成回数を参照・変更する。
    fun getUsage(ownerSub: String): Int = transaction {
        AiGenerationUsageTable.selectAll()
            .andWhere { AiGenerationUsageTable.ownerSub eq ownerSub }
            .singleOrNull()
            ?.get(AiGenerationUsageTable.generationCount) ?: 0
    }

    fun setUsage(ownerSub: String, count: Int) = transaction {
        val existing = AiGenerationUsageTable.selectAll()
            .andWhere { AiGenerationUsageTable.ownerSub eq ownerSub }
            .singleOrNull()
        if (existing == null) {
            AiGenerationUsageTable.insert {
                it[AiGenerationUsageTable.ownerSub] = ownerSub
                it[generationCount] = count
            }
        } else {
            AiGenerationUsageTable.update({ AiGenerationUsageTable.ownerSub eq ownerSub }) {
                it[generationCount] = count
            }
        }
    }

    fun get(storeId: Long): LpContentResponse? = transaction {
        LpContentsTable.selectAll().andWhere { LpContentsTable.storeId eq storeId }.singleOrNull()?.toResponse()
    }

    fun update(storeId: Long, request: UpdateLpContentRequest): LpContentResponse = transaction {
        val sanitizedHtml = sanitizeGeneratedHtml(request.pageHtml)
        val updated = LpContentsTable.update({ LpContentsTable.storeId eq storeId }) {
            it[catchphrase] = request.catchphrase
            it[pageHtml] = sanitizedHtml
        }
        if (updated == 0) throw LpContentNotFoundException()
        LpContentsTable.selectAll().andWhere { LpContentsTable.storeId eq storeId }.single().toResponse()
    }

    suspend fun generate(storeId: Long, ownerSub: String, generationContext: String): LpContentResponse {
        val used = transaction {
            AiGenerationUsageTable.selectAll()
                .andWhere { AiGenerationUsageTable.ownerSub eq ownerSub }
                .singleOrNull()
                ?.get(AiGenerationUsageTable.generationCount) ?: 0
        }
        if (used >= FREE_PLAN_LIFETIME_GENERATION_LIMIT) throw LpGenerationLimitExceededException()

        val qaText = interviewRepository.getState(storeId).messages.joinToString("\n") {
            "${if (it.role == "assistant") "Q" else "A"}: ${it.content}"
        }
        val prompt = buildString {
            appendLine(generationContext)
            appendLine()
            appendLine("以下はヒアリングの質疑応答です。")
            appendLine(qaText)
        }
        val raw = claudeClient.complete(GENERATION_SYSTEM_PROMPT, listOf(ClaudeMessage("user", prompt)))
        val parsed = parseGenerationResult(raw)
        val sanitizedHtml = sanitizeGeneratedHtml(parsed.pageHtml)

        transaction {
            val exists = LpContentsTable.selectAll().andWhere { LpContentsTable.storeId eq storeId }.any()
            if (exists) {
                LpContentsTable.update({ LpContentsTable.storeId eq storeId }) {
                    it[catchphrase] = parsed.catchphrase
                    it[pageHtml] = sanitizedHtml
                }
            } else {
                LpContentsTable.insert {
                    it[LpContentsTable.storeId] = storeId
                    it[catchphrase] = parsed.catchphrase
                    it[pageHtml] = sanitizedHtml
                }
            }

            val existingUsage = AiGenerationUsageTable.selectAll()
                .andWhere { AiGenerationUsageTable.ownerSub eq ownerSub }
                .singleOrNull()
            if (existingUsage == null) {
                AiGenerationUsageTable.insert {
                    it[AiGenerationUsageTable.ownerSub] = ownerSub
                    it[generationCount] = 1
                }
            } else {
                AiGenerationUsageTable.update({ AiGenerationUsageTable.ownerSub eq ownerSub }) {
                    it[generationCount] = existingUsage[AiGenerationUsageTable.generationCount] + 1
                }
            }
        }
        interviewRepository.reset(storeId)

        return LpContentResponse(catchphrase = parsed.catchphrase, pageHtml = sanitizedHtml)
    }

    private fun parseGenerationResult(raw: String): GenerationResult {
        val cleaned = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        return Json.decodeFromString(cleaned)
    }

    private fun ResultRow.toResponse(): LpContentResponse = LpContentResponse(
        catchphrase = this[LpContentsTable.catchphrase],
        pageHtml = this[LpContentsTable.pageHtml] ?: "",
    )
}
