package com.soma.plugins.huggingface

import com.soma.plugin.api.IAgentPlugin
import com.soma.plugin.api.AgentRequest
import com.soma.plugin.api.AgentResponse
import com.soma.plugin.runtime.*
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
 * Реализует LEGO-архитектуру SOMA AI с расширенными runtime возможностями
 */
class HuggingFacePlugin : IAgentPlugin, EventBusListener {
    
    private val logger = LoggerFactory.getLogger(HuggingFacePlugin::class.java)
    
    override val name: String = "HuggingFace Online Agent"
    override val version: String = "1.1.0"
    override val description: String = "Online AI inference using HuggingFace models"
    
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
    
    // Runtime состояние
    private var pluginContext: PluginContext? = null
    private var session: PluginSession? = null
    private var isLoaded = false
    private var config: HuggingFaceConfig? = null
    
    private val httpClient: OkHttpClient by lazy {
        val cfg = config ?: createFallbackConfig()
        OkHttpClient.Builder()
            .connectTimeout(cfg.timeouts.connectTimeoutMs, TimeUnit.MILLISECONDS)
            .readTimeout(cfg.timeouts.readTimeoutMs, TimeUnit.MILLISECONDS)
            .writeTimeout(cfg.timeouts.writeTimeoutMs, TimeUnit.MILLISECONDS)
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
            
            // Обновляем сессию
            session?.touch()
            
            // Публикуем событие начала обработки
            pluginContext?.eventBus?.post(
                PluginEvent("request.started", 
                    mapOf("plugin" to name, "request_type" to request.type),
                    name)
            )
            
            // Сохраняем статистику
            pluginContext?.sharedContext?.increment("hf_requests_total")
            
            val task = detectTask(request)
            val model = selectModel(request, task)
            val response = makeApiRequest(request.content, model, task)
            
            logger.info("HuggingFace response generated successfully")
            
            // Публикуем событие успешного завершения
            pluginContext?.eventBus?.post(
                PluginEvent("request.completed",
                    mapOf("plugin" to name, "success" to true, "model" to model),
                    name)
            )
            
            AgentResponse(
                success = true,
                content = response,
                metadata = mapOf(
                    "model" to model,
                    "task" to task.taskName,
                    "plugin" to name,
                    "version" to version,
                    "timestamp" to System.currentTimeMillis(),
                    "api" to "huggingface",
                    "session_id" to (session?.sessionId ?: "none")
                )
            )
            
        } catch (e: Exception) {
            logger.error("Error processing HuggingFace request", e)
            
            // Публикуем событие ошибки
            pluginContext?.eventBus?.post(PluginEvent.error(e, name))
            
            if (config?.features?.enableFallback == true) {
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
    
    override fun onLoad(context: Any?) {
        if (context is PluginContext) {
            this.pluginContext = context
            this.session = context.session
            this.isLoaded = true
            
            logger.info("HuggingFace Plugin loaded with context")
            
            // Загружаем конфигурацию
            this.config = loadConfig()
            
            // Подписываемся на события
            context.eventBus.subscribe(this)
            
            // Сохраняем статистику
            context.sharedContext.set("hf_loaded_at", System.currentTimeMillis())
            context.sharedContext.set("hf_requests_total", 0)
            context.sharedContext.set("hf_models_cache", mutableMapOf<String, Any>())
            
            // Публикуем событие загрузки
            context.eventBus.post(PluginEvent.pluginLoaded(name))
            
            logger.info("HuggingFace Plugin initialized successfully")
        } else {
            logger.warn("HuggingFace Plugin loaded without proper context")
        }
    }
    
    override fun onUnload() {
        logger.info("Unloading HuggingFace Plugin")
        
        // Отписываемся от событий
        pluginContext?.eventBus?.unsubscribe(this)
        
        // Публикуем событие выгрузки
        pluginContext?.eventBus?.post(
            PluginEvent("plugin.unloaded", name, "core")
        )
        
        // Закрываем HTTP клиент
        try {
            httpClient.dispatcher.executorService.shutdown()
            httpClient.connectionPool.evictAll()
        } catch (e: Exception) {
            logger.warn("Error closing HTTP client", e)
        }
        
        // Закрываем сессию
        session?.close()
        
        // Очищаем состояние
        this.isLoaded = false
        this.pluginContext = null
        this.session = null
        this.config = null
        
        logger.info("HuggingFace Plugin unloaded")
    }
    
    override fun onEvent(event: Any) {
        if (event is PluginEvent) {
            handlePluginEvent(event)
        }
    }
    
    // EventBusListener implementation
    override fun onEvent(event: PluginEvent) {
        handlePluginEvent(event)
    }
    
    override fun shouldHandle(event: PluginEvent): Boolean {
        return when (event.type) {
            PluginEvent.MEMORY_UPDATE -> true
            "config.updated" -> true
            "api.token.refreshed" -> true
            "system.shutdown" -> true
            else -> false
        }
    }
    
    override val priority: Int = 5
    override val listenerId: String = "huggingface-plugin"
    
    override fun isReady(): Boolean = isLoaded && config != null
    
    override fun getMetadata(): Map<String, Any> {
        return super.getMetadata() + mapOf(
            "model_type" to "online",
            "api_provider" to "huggingface",
            "supports_streaming" to false,
            "loaded" to isLoaded,
            "session_active" to (session?.isActive ?: false),
            "requests_processed" to (pluginContext?.sharedContext?.get<Int>("hf_requests_total") ?: 0),
            "config_loaded" to (config != null)
        )
    }
    
    /**
     * Обработка событий плагина
     */
    private fun handlePluginEvent(event: PluginEvent) {
        when (event.type) {
            PluginEvent.MEMORY_UPDATE -> {
                logger.debug("HF plugin received memory update: ${event.payload}")
                if (event.payload is Map<*, *>) {
                    @Suppress("UNCHECKED_CAST")
                    val data = event.payload as Map<String, Any>
                    
                    // Обновляем кеш моделей
                    data["hf_models"]?.let { models ->
                        pluginContext?.sharedContext?.set("hf_models_cache", models)
                    }
                }
            }
            "config.updated" -> {
                logger.info("Received config update request")
                config = loadConfig()
            }
            "api.token.refreshed" -> {
                logger.info("API token was refreshed")
                config = loadConfig()
            }
            "system.shutdown" -> {
                logger.info("Received system shutdown signal")
                onUnload()
            }
            else -> {
                logger.debug("Received unhandled event: ${event.type}")
            }
        }
    }
    
    // Остальные методы остаются без изменений, но с обновленными вызовами config
    
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
    
    private fun selectModel(request: AgentRequest, task: HuggingFaceTask): String {
        val requestedModel = request.metadata["model"]?.toString()
        
        if (requestedModel != null) {
            return requestedModel
        }
        
        val cfg = config ?: createFallbackConfig()
        val availableModels = cfg.models[task.taskName]
        return availableModels?.firstOrNull() ?: cfg.defaultModel
    }
    
    private suspend fun makeApiRequest(content: String, model: String, task: HuggingFaceTask): String = withContext(Dispatchers.IO) {
        val cfg = config ?: createFallbackConfig()
        val url = "${cfg.apiBaseUrl}/models/$model"
        
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
            .addHeader("Authorization", "Bearer ${cfg.apiToken}")
            .addHeader("Content-Type", JSON_MEDIA_TYPE)
            .post(requestBody)
            .build()
        
        var lastException: Exception? = null
        
        repeat(cfg.retry.maxAttempts) { attempt ->
            try {
                val response = httpClient.newCall(request).execute()
                return@withContext processResponse(response, task)
            } catch (e: Exception) {
                lastException = e
                logger.warn("Attempt ${attempt + 1} failed", e)
                
                if (attempt < cfg.retry.maxAttempts - 1) {
                    delay(cfg.retry.delayMs)
                }
            }
        }
        
        throw lastException ?: IOException("All retry attempts failed")
    }
    
    private fun buildParameters(task: HuggingFaceTask): Map<String, Any> {
        val cfg = config ?: createFallbackConfig()
        val baseParams = mutableMapOf<String, Any>(
            "temperature" to cfg.inference.temperature,
            "top_k" to cfg.inference.topK,
            "top_p" to cfg.inference.topP,
            "do_sample" to cfg.inference.doSample
        )
        
        when (task) {
            HuggingFaceTask.TEXT_GENERATION -> {
                baseParams["max_new_tokens"] = cfg.inference.maxTokens
                baseParams["return_full_text"] = false
            }
            HuggingFaceTask.SUMMARIZATION -> {
                baseParams["max_length"] = cfg.inference.maxTokens
                baseParams["min_length"] = 10
            }
            else -> {
                // Для остальных задач используем базовые параметры
            }
        }
        
        return baseParams
    }
    
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
    
    private fun parseResponseContent(responseBody: String, task: HuggingFaceTask): String {
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
