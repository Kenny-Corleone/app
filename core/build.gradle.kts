plugins {
    kotlin("jvm")
}

dependencies {
    api("androidx.annotation:annotation:1.7.0")
    
    // Kotlin coroutines для suspend функций
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

kotlin {
    jvmToolchain(8)
}
