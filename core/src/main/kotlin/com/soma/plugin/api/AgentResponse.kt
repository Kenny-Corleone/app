package com.soma.plugin.api

/**
 * Ответ агента
 */
data class AgentResponse(
    val success: Boolean,
    val content: String,
    val metadata: Map<String, Any> = emptyMap(),
    val error: String? = null
)
