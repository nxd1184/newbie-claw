plugins {
    id("org.springframework.boot") version "3.4.5"
    id("io.spring.dependency-management") version "1.1.7"
    kotlin("jvm") version "2.1.0"
    kotlin("plugin.spring") version "2.1.0"
}

group = "dev.stevennguyen.newbieclaw"
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

    // Kotlin coroutines — parallel MAP calls for faster local-LLM reviews
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")

    // Kotlin reflection — required by Spring
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // PDF processing — extract text from PDF files
    implementation("org.apache.pdfbox:pdfbox:3.0.3")
    
    // OCR for scanned PDFs — Tesseract integration
    implementation("net.sourceforge.tess4j:tess4j:5.13.0")
}

// Override Spring Boot BOM versions to fix security vulnerabilities
ext["spring-framework.version"] = "6.2.11"
ext["jackson-bom.version"] = "2.18.6"
ext["logback.version"] = "1.5.26"
ext["commons-lang3.version"] = "3.18.0"

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
