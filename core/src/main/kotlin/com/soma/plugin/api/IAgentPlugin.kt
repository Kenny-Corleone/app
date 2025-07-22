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
}
