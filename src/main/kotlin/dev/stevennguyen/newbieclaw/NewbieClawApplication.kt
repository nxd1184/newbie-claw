package dev.stevennguyen.newbieclaw

import org.springframework.boot.runApplication
import dev.stevennguyen.newbieclaw.config.CodeReviewProperties
import dev.stevennguyen.newbieclaw.config.InvoiceExtractionProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties

@SpringBootApplication
@EnableConfigurationProperties(CodeReviewProperties::class, InvoiceExtractionProperties::class)
class NewbieClawApplication {
}

fun main(args: Array<String>) {
    runApplication<NewbieClawApplication>(*args)
}
