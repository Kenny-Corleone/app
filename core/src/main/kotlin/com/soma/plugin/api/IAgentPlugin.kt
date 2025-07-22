package com.soma.plugin.api

/**
 * Базовый интерфейс для всех плагинов SOMA AI.
 * Реализует LEGO-архитектуру для расширяемых агентов.
 */
interface IAgentPlugin {
    /**
     * Уникальное имя плагина
     */
    val name: String
    
    /**
     * Проверяет, может ли плагин обработать данный запрос
     * @param request запрос к агенту
     * @return true если плагин может обработать запрос
     */
    fun canHandle(request: AgentRequest): Boolean
    
    /**
     * Обрабатывает запрос к агенту
     * @param request запрос к агенту
     * @return ответ агента
     */
    suspend fun handle(request: AgentRequest): AgentResponse
    
    // Lifecycle методы (опциональные с default реализацией)
    
    /**
     * Вызывается при загрузке плагина
     * @param context контекст плагина (будет доступен через plugin-runtime)
     */
    fun onLoad(context: Any? = null) = Unit
    
    /**
     * Вызывается при выгрузке плагина
     */
    fun onUnload() = Unit
    
    /**
     * Вызывается при получении события через EventBus
     * @param event событие (будет типизировано в plugin-runtime)
     */
    fun onEvent(event: Any) = Unit
    
    /**
     * Версия плагина
     */
    val version: String get() = "1.0.0"
    
    /**
     * Описание плагина
     */
    val description: String get() = "SOMA AI Plugin"
    
    /**
     * Автор плагина
     */
    val author: String get() = "SOMA AI Team"
    
    /**
     * Проверка готовности плагина к работе
     */
    fun isReady(): Boolean = true
    
    /**
     * Получение метаданных плагина
     */
    fun getMetadata(): Map<String, Any> = mapOf(
        "name" to name,
        "version" to version,
        "description" to description,
        "author" to author,
        "ready" to isReady()
    )
}
