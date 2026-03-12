package dev.stevennguyen.newbieclaw

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import dev.stevennguyen.newbieclaw.agent.CodeReviewProperties
import dev.stevennguyen.newbieclaw.agent.InvoiceExtractionProperties

@SpringBootApplication
@EnableConfigurationProperties(CodeReviewProperties::class, InvoiceExtractionProperties::class)
class NewbieClawApplication

fun main(args: Array<String>) {
    runApplication<NewbieClawApplication>(*args)
}
