package com.soma.app

import com.soma.plugin.api.AgentRequest
import com.soma.plugin.api.IAgentPlugin
import com.soma.plugin.runtime.*
import com.soma.plugins.llama.LlamaPlugin
import com.soma.plugins.huggingface.HuggingFacePlugin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory

/**
 * Основное приложение SOMA AI с расширенными runtime возможностями
 */
class SomaApplication {
    
    private val logger = LoggerFactory.getLogger(SomaApplication::class.java)
    
    // Создаем плагины
    private val llamaPlugin = LlamaPlugin()
    private val huggingfacePlugin = HuggingFacePlugin()
    
    // Регистрируем все доступные плагины
    private val agentPlugins: Set<IAgentPlugin> = setOf(
        llamaPlugin,
        huggingfacePlugin
    )
    
    // Demo EventBus listener
    private val demoEventListener = object : TypedEventBusListener(PluginEvent.MEMORY_UPDATE) {
        override fun onEvent(event: PluginEvent) {
            logger.info("📢 Demo listener received event: ${event.type} from ${event.source}")
            logger.info("📄 Event payload: ${event.payload}")
        }
        
        override val listenerId: String = "demo-listener"
        override val priority: Int = 100 // Высокий приоритет
    }
    
    init {
        logger.info("🚀 SOMA AI Application with Runtime Features initialized")
        
        // Подписываемся на глобальные события
        EventBus.subscribe(demoEventListener)
        
        // Инициализируем плагины с runtime контекстом
        initializePlugins()
        
        logger.info("✅ Available plugins: ${agentPlugins.map { it.name }}")
        logger.info("📊 EventBus stats: ${EventBus.getStats()}")
        logger.info("🧠 SharedContext stats: ${SharedContext.getStats()}")
    }
    
    /**
     * Инициализация плагинов с runtime контекстом
     */
    private fun initializePlugins() {
        agentPlugins.forEach { plugin ->
            val context = DefaultPluginContext(
                pluginName = plugin.name,
                version = plugin.version,
                config = getPluginConfig(plugin.name)
            )
            
            plugin.onLoad(context)
            logger.info("🔌 Plugin loaded: ${plugin.name} v${plugin.version}")
        }
    }
    
    /**
     * Получение конфигурации плагина
     */
    private fun getPluginConfig(pluginName: String): Map<String, Any> {
        return when (pluginName) {
            "LLaMA Offline Agent" -> mapOf(
                "model_path" to "models/llama-2-7b.gguf",
                "max_tokens" to 512,
                "temperature" to 0.7
            )
            "HuggingFace Online Agent" -> mapOf(
                "api_base" to "https://api-inference.huggingface.co",
                "default_model" to "gpt2",
                "timeout_ms" to 30000
            )
            else -> emptyMap()
        }
    }
    
    /**
     * Обрабатывает запрос, находя подходящий плагин
     */
    suspend fun processRequest(request: AgentRequest): String {
        logger.info("�� Processing request of type: ${request.type}")
        
        val availablePlugin = agentPlugins.find { it.canHandle(request) }
        
        return if (availablePlugin != null) {
            logger.info("🎯 Using plugin: ${availablePlugin.name}")
            val response = availablePlugin.handle(request)
            
            if (response.success) {
                response.content
            } else {
                "❌ Error: ${response.error}"
            }
        } else {
            "❓ No suitable plugin found for request type: ${request.type}"
        }
    }
    
    /**
     * Демонстрация EventBus возможностей
     */
    private suspend fun demonstrateEventBus() {
        logger.info("\n🎪 === EventBus Demo ===")
        
        // Публикуем событие обновления памяти
        logger.info("📤 Publishing memory update event...")
        EventBus.post(PluginEvent.memoryUpdate(
            mapOf("key" to "demo_value", "timestamp" to System.currentTimeMillis()),
            "core"
        ))
        
        delay(100) // Даем время обработать событие
        
        // Публикуем кастомное событие
        logger.info("📤 Publishing custom event...")
        EventBus.event("demo.custom.event")
            .payload(mapOf("message" to "Hello from EventBus!", "number" to 42))
            .source("demo-app")
            .metadata("priority" to "high", "category" to "demo")
            .post()
        
        delay(100)
        
        // Асинхронная публикация
        logger.info("📤 Publishing async event...")
        EventBus.postAsync(PluginEvent("demo.async", "Async payload", "demo"))
        
        delay(200) // Даем время обработать все события
        
        logger.info("📊 EventBus final stats: ${EventBus.getStats()}")
    }
    
    /**
     * Демонстрация SharedContext возможностей
     */
    private fun demonstrateSharedContext() {
        logger.info("\n🧠 === SharedContext Demo ===")
        
        // Устанавливаем данные
        SharedContext.set("demo_string", "Hello SharedContext!")
        SharedContext.set("demo_number", 123)
        SharedContext.set("demo_map", mapOf("nested" to "value"))
        
        // Временные данные с TTL
        SharedContext.setTemporary("temp_data", "This will expire", 5000)
        
        // Increment
        repeat(3) {
            val counter = SharedContext.increment("demo_counter")
            logger.info("🔢 Counter incremented to: $counter")
        }
        
        // Подписка на изменения
        val changeListener = object : DataChangeListener {
            override fun onDataChanged(key: String, oldValue: Any?, newValue: Any?) {
                logger.info("🔄 Data changed: $key = $oldValue → $newValue")
            }
        }
        SharedContext.subscribe("watched_key", changeListener)
        
        // Изменяем отслеживаемый ключ
        SharedContext.set("watched_key", "initial_value")
        SharedContext.update<String>("watched_key") { it + "_updated" }
        
        // Показываем все данные
        val keys = SharedContext.keys()
        logger.info("🗂️ SharedContext keys: $keys")
        logger.info("📊 SharedContext stats: ${SharedContext.getStats()}")
        
        keys.forEach { key ->
            val value = SharedContext.get<Any>(key)
            logger.info("   💾 $key = $value")
        }
    }
    
    /**
     * Демонстрация PluginSession возможностей
     */
    private fun demonstratePluginSessions() {
        logger.info("\n🎫 === PluginSession Demo ===")
        
        // Создаем несколько сессий
        val session1 = PluginSessionManager.createSession()
        val session2 = PluginSessionManager.createSession()
        
        // Работаем с сессиями
        session1.set("user_id", "user123")
        session1.set("preferences", mapOf("theme" to "dark", "language" to "en"))
        session1.metadata["login_time"] = System.currentTimeMillis()
        
        session2.set("temp_data", "temporary content")
        session2.set("counter", 0)
        
        logger.info("📋 Session 1: ${session1}")
        logger.info("   👤 User ID: ${session1.get<String>("user_id")}")
        logger.info("   ⚙️  Preferences: ${session1.get<Map<String, Any>>("preferences")}")
        
        logger.info("📋 Session 2: ${session2}")
        logger.info("   📄 Temp data: ${session2.get<String>("temp_data")}")
        
        // Активные сессии
        val activeSessions = PluginSessionManager.getActiveSessions()
        logger.info("🔢 Active sessions: ${activeSessions.size}")
        
        // Закрываем одну сессию
        session2.close()
        logger.info("❌ Session 2 closed. Active sessions: ${PluginSessionManager.getActiveSessions().size}")
    }
    
    /**
     * Завершение работы приложения
     */
    fun shutdown() {
        logger.info("🛑 Shutting down SOMA AI...")
        
        // Публикуем событие завершения
        EventBus.post(PluginEvent("system.shutdown", null, "core"))
        
        // Выгружаем плагины
        agentPlugins.forEach { plugin ->
            plugin.onUnload()
            logger.info("🔌 Plugin unloaded: ${plugin.name}")
        }
        
        // Очищаем ресурсы
        SharedContext.clear()
        EventBus.shutdown()
        PluginSessionManager.cleanupInactiveSessions()
        
        logger.info("✅ SOMA AI shutdown complete")
    }
    
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val app = SomaApplication()
            
            println("\n🎯 === SOMA AI Advanced Plugin Demo ===")
            
            runBlocking {
                try {
                    // Демонстрация runtime возможностей
                    app.demonstrateSharedContext()
                    app.demonstratePluginSessions()
                    app.demonstrateEventBus()
                    
                    println("\n🤖 === AI Plugin Testing ===")
                    
                    // Тест LLaMA плагина
                    println("\n🦙 Testing LLaMA Plugin:")
                    val llamaRequest = AgentRequest(
                        type = "llama",
                        content = "What are the benefits of AI?",
                        metadata = mapOf("session" to "demo")
                    )
                    println(app.processRequest(llamaRequest))
                    
                    // Тест HuggingFace плагина  
                    println("\n🤗 Testing HuggingFace Plugin:")
                    val hfRequest = AgentRequest(
                        type = "huggingface",
                        content = "Artificial intelligence will transform",
                        metadata = mapOf("task" to "text-generation")
                    )
                    println(app.processRequest(hfRequest))
                    
                    // Публикуем финальное событие
                    println("\n📢 Publishing final memory update...")
                    EventBus.post(PluginEvent.memoryUpdate(
                        mapOf(
                            "final_stats" to mapOf(
                                "requests_processed" to 2,
                                "plugins_active" to app.agentPlugins.size,
                                "demo_completed" to true
                            )
                        ),
                        "demo-app"
                    ))
                    
                    delay(200) // Время для обработки события
                    
                    println("\n📊 === Final Statistics ===")
                    println("EventBus: ${EventBus.getStats()}")
                    println("SharedContext: ${SharedContext.getStats()}")
                    
                    // Показываем метаданные плагинов
                    println("\n🔌 === Plugin Metadata ===")
                    app.agentPlugins.forEach { plugin ->
                        println("${plugin.name}:")
                        plugin.getMetadata().forEach { (key, value) ->
                            println("  $key: $value")
                        }
                    }
                    
                } finally {
                    app.shutdown()
                }
            }
        }
    }
}
