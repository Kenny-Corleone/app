package com.soma.app

import com.soma.plugin.api.AgentRequest
import com.soma.plugin.api.IAgentPlugin
import com.soma.plugins.llama.LlamaPlugin
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory

/**
 * Основное приложение SOMA AI с поддержкой плагинов
 */
class SomaApplication {
    
    private val logger = LoggerFactory.getLogger(SomaApplication::class.java)
    
    // Регистрируем плагины напрямую (без DI для простоты)
    private val agentPlugins: Set<IAgentPlugin> = setOf(
        LlamaPlugin()
    )
    
    init {
        logger.info("SOMA AI Application initialized")
        logger.info("Available plugins: ${agentPlugins.map { it.name }}")
    }
    
    /**
     * Обрабатывает запрос, находя подходящий плагин
     */
    suspend fun processRequest(request: AgentRequest): String {
        logger.info("Processing request of type: ${request.type}")
        
        val availablePlugin = agentPlugins.find { it.canHandle(request) }
        
        return if (availablePlugin != null) {
            logger.info("Using plugin: ${availablePlugin.name}")
            val response = availablePlugin.handle(request)
            
            if (response.success) {
                response.content
            } else {
                "Error: ${response.error}"
            }
        } else {
            "No suitable plugin found for request type: ${request.type}"
        }
    }
    
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val app = SomaApplication()
            
            println("=== SOMA AI Demo ===")
            
            // Пример использования
            runBlocking {
                val llamaRequest = AgentRequest(
                    type = "llama",
                    content = "What is artificial intelligence?"
                )
                
                val offlineRequest = AgentRequest(
                    type = "offline",
                    content = "Explain machine learning",
                    metadata = mapOf("model" to "llama")
                )
                
                val unknownRequest = AgentRequest(
                    type = "gpt",
                    content = "This won't be handled"
                )
                
                println("\n1. LLaMA Request:")
                println(app.processRequest(llamaRequest))
                
                println("\n2. Offline Request:")
                println(app.processRequest(offlineRequest))
                
                println("\n3. Unknown Request:")
                println(app.processRequest(unknownRequest))
            }
        }
    }
}
