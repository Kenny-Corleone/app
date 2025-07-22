package com.soma.plugin.runtime

/**
 * Событие плагина для системы EventBus
 */
data class PluginEvent(
    /**
     * Тип события (например: "memory.update", "session.created", "model.loaded")
     */
    val type: String,
    
    /**
     * Полезная нагрузка события
     */
    val payload: Any?,
    
    /**
     * Источник события (название плагина или системного компонента)
     */
    val source: String,
    
    /**
     * Временная метка создания события
     */
    val timestamp: Long = System.currentTimeMillis(),
    
    /**
     * Дополнительные метаданные события
     */
    val metadata: Map<String, Any> = emptyMap()
) {
    
    companion object {
        // Стандартные типы событий
        const val MEMORY_UPDATE = "memory.update"
        const val SESSION_CREATED = "session.created"
        const val SESSION_DESTROYED = "session.destroyed"
        const val PLUGIN_LOADED = "plugin.loaded"
        const val PLUGIN_UNLOADED = "plugin.unloaded"
        const val MODEL_LOADED = "model.loaded"
        const val MODEL_UNLOADED = "model.unloaded"
        const val REQUEST_STARTED = "request.started"
        const val REQUEST_COMPLETED = "request.completed"
        const val ERROR_OCCURRED = "error.occurred"
        
        /**
         * Создание события обновления памяти
         */
        fun memoryUpdate(data: Map<String, Any>, source: String): PluginEvent {
            return PluginEvent(MEMORY_UPDATE, data, source)
        }
        
        /**
         * Создание события загрузки плагина
         */
        fun pluginLoaded(pluginName: String): PluginEvent {
            return PluginEvent(PLUGIN_LOADED, pluginName, "core")
        }
        
        /**
         * Создание события ошибки
         */
        fun error(error: Throwable, source: String): PluginEvent {
            return PluginEvent(ERROR_OCCURRED, error.message, source, metadata = mapOf(
                "exception_type" to error.javaClass.simpleName,
                "stacktrace" to error.stackTraceToString()
            ))
        }
    }
}
