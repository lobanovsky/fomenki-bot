plugins {
    kotlin("jvm") version "2.1.20"
    kotlin("plugin.serialization") version "2.1.20"
    application
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

application {
    mainClass = "FomenkiBotKt"
}

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

val ktorVersion = "3.1.0"

dependencies {
    implementation("io.github.kotlin-telegram-bot.kotlin-telegram-bot:telegram:6.3.0")
    // kotlin-telegram-bot exposes retrofit2.Response in its public API but ships Retrofit as
    // implementation, so we need this on the compile classpath directly.
    implementation("com.squareup.retrofit2:retrofit:2.9.0")

    // Ktor server (webhook endpoint)
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")

    // Ktor client (backend API)
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")

    // JSON serialization
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")

    implementation("ch.qos.logback:logback-classic:1.5.18")
    implementation("ch.qos.logback:logback-core:1.5.18")

    testImplementation(kotlin("test"))
}
