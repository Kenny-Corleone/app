plugins {
    kotlin("jvm")
}

dependencies {
    implementation(project(":core"))
    
    // TODO: Добавить LLaMA Java binding после уточнения API
    // implementation("de.kherud:llama:4.2.0")
    
    // Coroutines для async операций
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    
    // Dependency Injection
    implementation("javax.inject:javax.inject:1")
    
    // Logging
    implementation("org.slf4j:slf4j-api:1.7.36")
    implementation("ch.qos.logback:logback-classic:1.2.12")
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}
