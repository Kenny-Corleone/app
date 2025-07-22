// Базовая конфигурация для плагинов
// Каждый конкретный плагин будет иметь свой собственный build.gradle.kts

plugins {
    kotlin("jvm")
}

dependencies {
    // Зависимость от core модуля для доступа к IAgentPlugin
    implementation(project(":core"))
}
