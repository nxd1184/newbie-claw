package com.example.codereview

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import com.example.codereview.agent.CodeReviewProperties

@SpringBootApplication
@EnableConfigurationProperties(CodeReviewProperties::class)
class CodeReviewApplication

fun main(args: Array<String>) {
    runApplication<CodeReviewApplication>(*args)
}
