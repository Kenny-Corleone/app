package com.soma.plugins.huggingface

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Data классы для работы с HuggingFace Inference API
 */

@JsonClass(generateAdapter = true)
data class HuggingFaceRequest(
    @Json(name = "inputs") val inputs: String,
    @Json(name = "parameters") val parameters: Map<String, Any>? = null,
    @Json(name = "options") val options: Map<String, Any>? = null
)

@JsonClass(generateAdapter = true)
data class HuggingFaceResponse(
    @Json(name = "generated_text") val generatedText: String? = null,
    @Json(name = "summary_text") val summaryText: String? = null,
    @Json(name = "translation_text") val translationText: String? = null,
    @Json(name = "label") val label: String? = null,
    @Json(name = "score") val score: Double? = null,
    @Json(name = "answer") val answer: String? = null,
    @Json(name = "start") val start: Int? = null,
    @Json(name = "end") val end: Int? = null
)

@JsonClass(generateAdapter = true)
data class HuggingFaceError(
    @Json(name = "error") val error: String? = null,
    @Json(name = "warnings") val warnings: List<String>? = null,
    @Json(name = "estimated_time") val estimatedTime: Double? = null
)

@JsonClass(generateAdapter = true)
data class HuggingFaceConfig(
    @Json(name = "api_token") val apiToken: String,
    @Json(name = "default_model") val defaultModel: String,
    @Json(name = "api_base_url") val apiBaseUrl: String,
    @Json(name = "inference") val inference: InferenceConfig,
    @Json(name = "models") val models: Map<String, List<String>>,
    @Json(name = "timeouts") val timeouts: TimeoutConfig,
    @Json(name = "retry") val retry: RetryConfig,
    @Json(name = "features") val features: FeatureConfig
)

@JsonClass(generateAdapter = true)
data class InferenceConfig(
    @Json(name = "temperature") val temperature: Double,
    @Json(name = "top_k") val topK: Int,
    @Json(name = "top_p") val topP: Double,
    @Json(name = "max_tokens") val maxTokens: Int,
    @Json(name = "do_sample") val doSample: Boolean
)

@JsonClass(generateAdapter = true)
data class TimeoutConfig(
    @Json(name = "connect_timeout_ms") val connectTimeoutMs: Long,
    @Json(name = "read_timeout_ms") val readTimeoutMs: Long,
    @Json(name = "write_timeout_ms") val writeTimeoutMs: Long
)

@JsonClass(generateAdapter = true)
data class RetryConfig(
    @Json(name = "max_attempts") val maxAttempts: Int,
    @Json(name = "delay_ms") val delayMs: Long
)

@JsonClass(generateAdapter = true)
data class FeatureConfig(
    @Json(name = "enable_logging") val enableLogging: Boolean,
    @Json(name = "enable_fallback") val enableFallback: Boolean,
    @Json(name = "cache_responses") val cacheResponses: Boolean
)

/**
 * Типы задач HuggingFace
 */
enum class HuggingFaceTask(val taskName: String) {
    TEXT_GENERATION("text-generation"),
    SUMMARIZATION("summarization"),
    TRANSLATION("translation"),
    TEXT_CLASSIFICATION("text-classification"),
    QUESTION_ANSWERING("question-answering"),
    FILL_MASK("fill-mask"),
    TOKEN_CLASSIFICATION("token-classification"),
    FEATURE_EXTRACTION("feature-extraction")
}
