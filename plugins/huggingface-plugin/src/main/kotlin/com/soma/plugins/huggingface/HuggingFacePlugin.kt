package com.soma.plugins.huggingface

import com.soma.plugin.api.IAgentPlugin
import com.soma.plugin.api.AgentRequest
import com.soma.plugin.api.AgentResponse
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.slf4j.LoggerFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * HuggingFace плагин для онлайн обработки запросов через HuggingFace Inference API
 * Реализует LEGO-архитектуру SOMA AI
 */
class HuggingFacePlugin : IAgentPlugin {
    
    private val logger = LoggerFactory.getLogger(HuggingFacePlugin::class.java)
    
    override val name: String = "HuggingFace Online Agent"
    
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
    
    private val config: HuggingFaceConfig by lazy { loadConfig() }
    
    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(config.timeouts.connectTimeoutMs, TimeUnit.MILLISECONDS)
            .readTimeout(config.timeouts.readTimeoutMs, TimeUnit.MILLISECONDS)
            .writeTimeout(config.timeouts.writeTimeoutMs, TimeUnit.MILLISECONDS)
            .build()
    }
    
    companion object {
        private const val MODEL_TYPE_HUGGINGFACE = "huggingface"
        private const val MODEL_TYPE_HF = "hf"
        private const val MODEL_TYPE_ONLINE = "online"
        private const val JSON_MEDIA_TYPE = "application/json"
    }
    
    override fun canHandle(request: AgentRequest): Boolean {
        val requestType = request.type.lowercase()
        val modelFromMetadata = request.metadata["model"]?.toString()?.lowercase()
        
        return requestType == MODEL_TYPE_HUGGINGFACE ||
               requestType == MODEL_TYPE_HF ||
               requestType == MODEL_TYPE_ONLINE ||
               modelFromMetadata?.contains("hf") == true ||
               modelFromMetadata?.contains("huggingface") == true
    }
    
    override suspend fun handle(request: AgentRequest): AgentResponse {
        return try {
            logger.info("Processing HuggingFace request: ${request.type}")
            
            val task = detectTask(request)
            val model = selectModel(request, task)
            val response = makeApiRequest(request.content, model, task)
            
            logger.info("HuggingFace response generated successfully")
            
            AgentResponse(
                success = true,
                content = response,
                metadata = mapOf(
                    "model" to model,
                    "task" to task.taskName,
                    "plugin" to name,
                    "timestamp" to System.currentTimeMillis(),
                    "api" to "huggingface"
                )
            )
            
        } catch (e: Exception) {
            logger.error("Error processing HuggingFace request", e)
            
            if (config.features.enableFallback) {
                handleFallback(request, e)
            } else {
                AgentResponse(
                    success = false,
                    content = "",
                    metadata = mapOf(
                        "plugin" to name,
                        "error_type" to e.javaClass.simpleName
                    ),
                    error = "HuggingFace processing failed: ${e.message}"
                )
            }
        }
    }
    
    /**
     * Загрузка конфигурации из ресурсов
     */
    private fun loadConfig(): HuggingFaceConfig {
        return try {
            val configStream = javaClass.classLoader
                .getResourceAsStream("huggingface-config.json")
                ?: throw IllegalStateException("Config file not found")
            
            val configJson = configStream.bufferedReader().use { it.readText() }
            val adapter = moshi.adapter(HuggingFaceConfig::class.java)
            
            adapter.fromJson(configJson) ?: throw IllegalStateException("Invalid config format")
        } catch (e: Exception) {
            logger.warn("Failed to load config, using fallback", e)
            createFallbackConfig()
        }
    }
    
    /**
     * Создание fallback конфигурации
     */
    private fun createFallbackConfig(): HuggingFaceConfig {
        return HuggingFaceConfig(
            apiToken = System.getenv("HUGGINGFACE_API_TOKEN") ?: "hf_fallback_token",
            defaultModel = "gpt2",
            apiBaseUrl = "https://api-inference.huggingface.co",
            inference = InferenceConfig(0.7, 50, 0.9, 256, true),
            models = mapOf(
                "text-generation" to listOf("gpt2"),
                "summarization" to listOf("facebook/bart-large-cnn")
            ),
            timeouts = TimeoutConfig(10000, 30000, 10000),
            retry = RetryConfig(3, 1000),
            features = FeatureConfig(true, true, false)
        )
    }
    
    /**
     * Определение типа задачи на основе запроса
     */
    private fun detectTask(request: AgentRequest): HuggingFaceTask {
        val content = request.content.lowercase()
        val taskHint = request.metadata["task"]?.toString()?.lowercase()
        
        return when {
            taskHint == "summarization" || content.contains("summarize") -> HuggingFaceTask.SUMMARIZATION
            taskHint == "translation" || content.contains("translate") -> HuggingFaceTask.TRANSLATION
            taskHint == "classification" || content.contains("classify") -> HuggingFaceTask.TEXT_CLASSIFICATION
            taskHint == "question-answering" || content.contains("answer") -> HuggingFaceTask.QUESTION_ANSWERING
            else -> HuggingFaceTask.TEXT_GENERATION
        }
    }
    
    /**
     * Выбор модели для задачи
     */
    private fun selectModel(request: AgentRequest, task: HuggingFaceTask): String {
        val requestedModel = request.metadata["model"]?.toString()
        
        if (requestedModel != null) {
            return requestedModel
        }
        
        val availableModels = config.models[task.taskName]
        return availableModels?.firstOrNull() ?: config.defaultModel
    }
    
    /**
     * Выполнение API запроса с повторами
     */
    private suspend fun makeApiRequest(content: String, model: String, task: HuggingFaceTask): String = withContext(Dispatchers.IO) {
        val url = "${config.apiBaseUrl}/models/$model"
        
        val requestData = HuggingFaceRequest(
            inputs = content,
            parameters = buildParameters(task),
            options = mapOf("wait_for_model" to true)
        )
        
        val adapter = moshi.adapter(HuggingFaceRequest::class.java)
        val requestBody = adapter.toJson(requestData)
            .toRequestBody(JSON_MEDIA_TYPE.toMediaType())
        
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${config.apiToken}")
            .addHeader("Content-Type", JSON_MEDIA_TYPE)
            .post(requestBody)
            .build()
        
        var lastException: Exception? = null
        
        repeat(config.retry.maxAttempts) { attempt ->
            try {
                val response = httpClient.newCall(request).execute()
                return@withContext processResponse(response, task)
            } catch (e: Exception) {
                lastException = e
                logger.warn("Attempt ${attempt + 1} failed", e)
                
                if (attempt < config.retry.maxAttempts - 1) {
                    delay(config.retry.delayMs)
                }
            }
        }
        
        throw lastException ?: IOException("All retry attempts failed")
    }
    
    /**
     * Построение параметров для запроса
     */
    private fun buildParameters(task: HuggingFaceTask): Map<String, Any> {
        val baseParams = mutableMapOf<String, Any>(
            "temperature" to config.inference.temperature,
            "top_k" to config.inference.topK,
            "top_p" to config.inference.topP,
            "do_sample" to config.inference.doSample
        )
        
        when (task) {
            HuggingFaceTask.TEXT_GENERATION -> {
                baseParams["max_new_tokens"] = config.inference.maxTokens
                baseParams["return_full_text"] = false
            }
            HuggingFaceTask.SUMMARIZATION -> {
                baseParams["max_length"] = config.inference.maxTokens
                baseParams["min_length"] = 10
            }
            else -> {
                // Для остальных задач используем базовые параметры
            }
        }
        
        return baseParams
    }
    
    /**
     * Обработка ответа API
     */
    private fun processResponse(response: Response, task: HuggingFaceTask): String {
        val responseBody = response.body?.string()
            ?: throw IOException("Empty response body")
        
        if (!response.isSuccessful) {
            val errorAdapter = moshi.adapter(HuggingFaceError::class.java)
            val error = try {
                errorAdapter.fromJson(responseBody)
            } catch (e: Exception) {
                null
            }
            
            throw IOException("API error: ${error?.error ?: "HTTP ${response.code}"}")
        }
        
        return parseResponseContent(responseBody, task)
    }
    
    /**
     * Парсинг содержимого ответа
     */
    private fun parseResponseContent(responseBody: String, task: HuggingFaceTask): String {
        // HuggingFace API может возвращать массив или объект
        return try {
            when (task) {
                HuggingFaceTask.TEXT_GENERATION -> {
                    val adapter = moshi.adapter(Array<HuggingFaceResponse>::class.java)
                    val responses = adapter.fromJson(responseBody)
                    responses?.firstOrNull()?.generatedText ?: "No text generated"
                }
                HuggingFaceTask.SUMMARIZATION -> {
                    val adapter = moshi.adapter(Array<HuggingFaceResponse>::class.java)
                    val responses = adapter.fromJson(responseBody)
                    responses?.firstOrNull()?.summaryText ?: "No summary generated"
                }
                HuggingFaceTask.TRANSLATION -> {
                    val adapter = moshi.adapter(Array<HuggingFaceResponse>::class.java)
                    val responses = adapter.fromJson(responseBody)
                    responses?.firstOrNull()?.translationText ?: "No translation generated"
                }
                HuggingFaceTask.TEXT_CLASSIFICATION -> {
                    val adapter = moshi.adapter(Array<Array<HuggingFaceResponse>>::class.java)
                    val responses = adapter.fromJson(responseBody)
                    val classification = responses?.firstOrNull()?.firstOrNull()
                    "${classification?.label} (${classification?.score})"
                }
                HuggingFaceTask.QUESTION_ANSWERING -> {
                    val adapter = moshi.adapter(HuggingFaceResponse::class.java)
                    val response = adapter.fromJson(responseBody)
                    response?.answer ?: "No answer found"
                }
                else -> {
                    "Response: $responseBody"
                }
            }
        } catch (e: Exception) {
            logger.warn("Failed to parse response, returning raw", e)
            "Raw response: $responseBody"
        }
    }
    
    /**
     * Обработка fallback в случае ошибки
     */
    private fun handleFallback(request: AgentRequest, error: Exception): AgentResponse {
        logger.info("Using fallback response for failed request")
        
        val fallbackContent = when {
            request.content.contains("translate", ignoreCase = true) -> 
                "Translation service temporarily unavailable. Please try again later."
            request.content.contains("summarize", ignoreCase = true) -> 
                "Summarization service temporarily unavailable. Please try again later."
            else -> 
                "HuggingFace service temporarily unavailable. Your request '${request.content}' could not be processed at this time."
        }
        
        return AgentResponse(
            success = true,
            content = fallbackContent,
            metadata = mapOf(
                "plugin" to name,
                "fallback" to true,
                "original_error" to (error.message ?: "Unknown error"),
                "timestamp" to System.currentTimeMillis()
            ),
            error = null
        )
    }
}
