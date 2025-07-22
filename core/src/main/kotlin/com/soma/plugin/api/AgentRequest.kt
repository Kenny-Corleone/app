package com.soma.plugin.api

/**
 * Запрос к агенту
 */
data class AgentRequest(
    val type: String,
    val content: String,
    val metadata: Map<String, Any> = emptyMap()
)
