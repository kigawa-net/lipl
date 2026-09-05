package net.kigawa.lipl.debug

import kotlinx.serialization.Serializable

@Serializable
data class DebugConfigResponse(val debugMenuEnabled: Boolean)

@Serializable
data class AiUsageResponse(val generationCount: Int, val limit: Int)

@Serializable
data class SetAiUsageRequest(val generationCount: Int)
