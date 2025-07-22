package com.soma.app

import com.soma.plugin.api.AgentRequest
import com.soma.plugin.api.IAgentPlugin
import com.soma.plugins.llama.LlamaPlugin
import com.soma.plugins.huggingface.HuggingFacePlugin
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory

/**
 * Основное приложение SOMA AI с поддержкой плагинов
 */
class SomaApplication {
    
    private val logger = LoggerFactory.getLogger(SomaApplication::class.java)
    
    // Регистрируем все доступные плагины
    private val agentPlugins: Set<IAgentPlugin> = setOf(
        LlamaPlugin(),
        HuggingFacePlugin()
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
            
            println("=== SOMA AI Plugin Demo ===")
            
            runBlocking {
                // Тест LLaMA плагина
                println("\n�� Testing LLaMA Plugin:")
                val llamaRequest = AgentRequest(
                    type = "llama",
                    content = "What is artificial intelligence?"
                )
                println(app.processRequest(llamaRequest))
                
                // Тест HuggingFace плагина - Text Generation
                println("\n🤗 Testing HuggingFace Plugin - Text Generation:")
                val hfTextRequest = AgentRequest(
                    type = "huggingface",
                    content = "The future of AI is",
                    metadata = mapOf("task" to "text-generation")
                )
                println(app.processRequest(hfTextRequest))
                
                // Тест HuggingFace плагина - Summarization
                println("\n📝 Testing HuggingFace Plugin - Summarization:")
                val hfSummaryRequest = AgentRequest(
                    type = "huggingface",
                    content = "Summarize: Artificial intelligence (AI) is intelligence demonstrated by machines, unlike the natural intelligence displayed by humans and animals, which involves consciousness and emotionality. The distinction between the former and the latter categories is often revealed by the acronym chosen. 'Strong' AI is usually labelled as AGI (Artificial General Intelligence) while attempts to emulate 'natural' intelligence have been called ABI (Artificial Biological Intelligence).",
                    metadata = mapOf("task" to "summarization")
                )
                println(app.processRequest(hfSummaryRequest))
                
                // Тест HuggingFace плагина - Translation
                println("\n🌍 Testing HuggingFace Plugin - Translation:")
                val hfTranslationRequest = AgentRequest(
                    type = "hf",
                    content = "Translate 'Hello, how are you?' to French",
                    metadata = mapOf("task" to "translation")
                )
                println(app.processRequest(hfTranslationRequest))
                
                // Тест с определением плагина по метаданным
                println("\n🔍 Testing Plugin Selection by Metadata:")
                val metadataRequest = AgentRequest(
                    type = "online",
                    content = "Generate creative text about space exploration",
                    metadata = mapOf("model" to "hf-gpt2")
                )
                println(app.processRequest(metadataRequest))
                
                // Тест неизвестного типа
                println("\n❓ Testing Unknown Request Type:")
                val unknownRequest = AgentRequest(
                    type = "unknown",
                    content = "This request won't be handled by any plugin"
                )
                println(app.processRequest(unknownRequest))
                
                println("\n✅ Demo completed!")
            }
        }
    }
}
