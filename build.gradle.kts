plugins {
    id("org.springframework.boot") version "3.4.2"
    id("io.spring.dependency-management") version "1.1.7"
    kotlin("jvm") version "2.1.0"
    kotlin("plugin.spring") version "2.1.0"
}

group = "com.example"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

val embabelVersion = "0.3.4"

repositories {
    mavenCentral()
    maven {
        name = "embabel-releases"
        url = uri("https://repo.embabel.com/artifactory/libs-release")
    }
    maven {
        name = "Spring Milestones"
        url = uri("https://repo.spring.io/milestone")
    }
}

dependencies {
    // Embabel shell starter — runs an interactive CLI to invoke agents
    implementation("com.embabel.agent:embabel-agent-starter-shell:$embabelVersion")

    // LLM providers — all three active; switch default-llm in application.yml to choose
    implementation("com.embabel.agent:embabel-agent-starter-google-genai:$embabelVersion")
    implementation("com.embabel.agent:embabel-agent-starter-lmstudio:$embabelVersion")
    implementation("com.embabel.agent:embabel-agent-starter-openai:$embabelVersion")
    implementation("com.embabel.agent:embabel-agent-starter-anthropic:$embabelVersion")

    // Kotlin reflection — required by Spring
    implementation("org.jetbrains.kotlin:kotlin-reflect")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// Forward terminal stdin so the Embabel interactive shell can receive input
tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    standardInput = System.`in`
}
