package com.soma.plugins.llama

import com.soma.plugin.api.IAgentPlugin
import com.soma.plugin.api.AgentRequest
import com.soma.plugin.api.AgentResponse
import com.soma.plugin.runtime.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

/**
 * LLaMA плагин для оффлайн обработки запросов через llama.cpp
 * Реализует LEGO-архитектуру SOMA AI с расширенными runtime возможностями
 */
class LlamaPlugin : IAgentPlugin, EventBusListener {
    
    private val logger = LoggerFactory.getLogger(LlamaPlugin::class.java)
    
    override val name: String = "LLaMA Offline Agent"
    override val version: String = "1.1.0"
    override val description: String = "Offline AI inference using LLaMA models"
    
    // Runtime состояние
    private var pluginContext: PluginContext? = null
    private var session: PluginSession? = null
    private var isLoaded = false
    
    companion object {
        private const val MODEL_TYPE_LLAMA = "llama"
        private const val MODEL_TYPE_OFFLINE = "offline"
        private const val DEFAULT_MODEL_PATH = "models/llama-2-7b.Q4_K_M.gguf"
    }
    
    override fun canHandle(request: AgentRequest): Boolean {
        val requestType = request.type.lowercase()
        val modelFromMetadata = request.metadata["model"]?.toString()?.lowercase()
        
        return requestType == MODEL_TYPE_LLAMA ||
               requestType == MODEL_TYPE_OFFLINE ||
               modelFromMetadata == MODEL_TYPE_LLAMA ||
               modelFromMetadata == MODEL_TYPE_OFFLINE
    }
    
    override suspend fun handle(request: AgentRequest): AgentResponse {
        return try {
            logger.info("Processing LLaMA request: ${request.type}")
            
            // Обновляем сессию
            session?.touch()
            
            // Публикуем событие начала обработки
            pluginContext?.eventBus?.post(
                PluginEvent.event("request.started")
                    .payload(mapOf("plugin" to name, "request_type" to request.type))
                    .source(name)
                    .build()
            )
            
            // Сохраняем статистику в SharedContext
            pluginContext?.sharedContext?.increment("llama_requests_total")
            
            val response = withContext(Dispatchers.IO) {
                processLlamaRequest(request.content)
            }
            
            logger.info("LLaMA response generated successfully")
            
            // Публикуем событие завершения обработки
            pluginContext?.eventBus?.post(
                PluginEvent("request.completed", 
                    mapOf("plugin" to name, "success" to true),
                    name)
            )
            
            AgentResponse(
                success = true,
                content = response,
                metadata = mapOf(
                    "model" to "llama",
                    "plugin" to name,
                    "version" to version,
                    "timestamp" to System.currentTimeMillis(),
                    "temperature" to 0.7,
                    "model_path" to DEFAULT_MODEL_PATH,
                    "session_id" to (session?.sessionId ?: "none")
                )
            )
            
        } catch (e: Exception) {
            logger.error("Error processing LLaMA request", e)
            
            // Публикуем событие ошибки
            pluginContext?.eventBus?.post(PluginEvent.error(e, name))
            
            AgentResponse(
                success = false,
                content = "",
                metadata = mapOf(
                    "plugin" to name,
                    "error_type" to e.javaClass.simpleName
                ),
                error = "LLaMA processing failed: ${e.message}"
            )
        }
    }
    
    override fun onLoad(context: Any?) {
        if (context is PluginContext) {
            this.pluginContext = context
            this.session = context.session
            this.isLoaded = true
            
            logger.info("LLaMA Plugin loaded with context")
            
            // Подписываемся на события
            context.eventBus.subscribe(this)
            
            // Сохраняем начальную статистику
            context.sharedContext.set("llama_loaded_at", System.currentTimeMillis())
            context.sharedContext.set("llama_requests_total", 0)
            
            // Публикуем событие загрузки
            context.eventBus.post(PluginEvent.pluginLoaded(name))
            
            logger.info("LLaMA Plugin initialized successfully")
        } else {
            logger.warn("LLaMA Plugin loaded without proper context")
        }
    }
    
    override fun onUnload() {
        logger.info("Unloading LLaMA Plugin")
        
        // Отписываемся от событий
        pluginContext?.eventBus?.unsubscribe(this)
        
        // Публикуем событие выгрузки
        pluginContext?.eventBus?.post(
            PluginEvent("plugin.unloaded", name, "core")
        )
        
        // Закрываем сессию
        session?.close()
        
        // Очищаем состояние
        this.isLoaded = false
        this.pluginContext = null
        this.session = null
        
        logger.info("LLaMA Plugin unloaded")
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
            "model.reload" -> true
            "system.shutdown" -> true
            else -> false
        }
    }
    
    override val priority: Int = 10
    override val listenerId: String = "llama-plugin"
    
    override fun isReady(): Boolean = isLoaded
    
    override fun getMetadata(): Map<String, Any> {
        return super.getMetadata() + mapOf(
            "model_type" to "offline",
            "supports_streaming" to false,
            "loaded" to isLoaded,
            "session_active" to (session?.isActive ?: false),
            "requests_processed" to (pluginContext?.sharedContext?.get<Int>("llama_requests_total") ?: 0)
        )
    }
    
    /**
     * Обработка событий плагина
     */
    private fun handlePluginEvent(event: PluginEvent) {
        when (event.type) {
            PluginEvent.MEMORY_UPDATE -> {
                logger.debug("Received memory update: ${event.payload}")
                // Можно обновить кеш модели или конфигурацию
            }
            "model.reload" -> {
                logger.info("Received model reload request")
                // TODO: Перезагрузка модели
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
    
    /**
     * Обработка запроса к LLaMA модели
     * TODO: Интеграция с реальной LLaMA библиотекой
     */
    private suspend fun processLlamaRequest(content: String): String {
        // Заглушка - в реальности здесь будет вызов LLaMA модели
        return "LLaMA Response: I'm a simulated response to '$content'. " +
               "This is an enhanced implementation with runtime features: " +
               "session tracking, event handling, and shared memory. " +
               "Session ID: ${session?.sessionId ?: "none"}"
    }
    
    /**
     * Освобождение ресурсов модели
     */
    fun dispose() {
        onUnload()
        logger.info("LLaMA plugin disposed")
    }
}
