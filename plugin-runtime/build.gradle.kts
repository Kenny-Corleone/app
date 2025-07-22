plugins {
    kotlin("jvm")
}

dependencies {
    // Зависимость от core модуля для доступа к базовым API
    api(project(":core"))
    
    // Coroutines для async операций
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    
    // Logging
    implementation("org.slf4j:slf4j-api:1.7.36")
    implementation("ch.qos.logback:logback-classic:1.2.12")
    
    // Collections для thread-safe операций
    implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable:0.3.5")
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}
