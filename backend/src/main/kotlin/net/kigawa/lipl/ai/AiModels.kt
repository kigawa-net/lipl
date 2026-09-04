package net.kigawa.lipl.ai

import kotlinx.serialization.Serializable

// TODO: 課金プラン（Free/Basic/Pro）未実装のため、全店舗をFreeプランとして扱う。
// プラン機能実装時にプランごとの回数（Basic:質問5回/月3回生成、Pro:質問10回/月6回生成）に置き換える。
const val FREE_PLAN_INTERVIEW_QUESTION_LIMIT = 3
const val FREE_PLAN_LIFETIME_GENERATION_LIMIT = 1

@Serializable
data class InterviewMessageResponse(
    val id: Long,
    val role: String,
    val content: String,
)

@Serializable
data class SendInterviewMessageRequest(
    val message: String? = null,
)

@Serializable
data class InterviewStateResponse(
    val messages: List<InterviewMessageResponse>,
    val questionCount: Int,
    val questionLimit: Int,
    val limitReached: Boolean,
)

@Serializable
data class LpContentResponse(
    val catchphrase: String,
    val description: String,
)

@Serializable
data class UpdateLpContentRequest(
    val catchphrase: String,
    val description: String,
)
