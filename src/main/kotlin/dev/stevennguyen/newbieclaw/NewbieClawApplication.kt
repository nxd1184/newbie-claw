package dev.stevennguyen.newbieclaw

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import com.embabel.agent.core.AgentPlatform
import dev.stevennguyen.newbieclaw.config.CodeReviewProperties
import dev.stevennguyen.newbieclaw.config.InvoiceExtractionProperties
import dev.stevennguyen.newbieclaw.config.AppSenseProperties
import dev.stevennguyen.newbieclaw.service.appsense.AppSenseSchedulerService
import dev.stevennguyen.newbieclaw.service.appsense.AppSensePatientService
import dev.stevennguyen.newbieclaw.agent.AppSenseSchedulerAgent
import dev.stevennguyen.newbieclaw.agent.AppSensePatientAgent

@SpringBootApplication
@EnableConfigurationProperties(CodeReviewProperties::class, InvoiceExtractionProperties::class, AppSenseProperties::class)
class NewbieClawApplication {
    
    @Bean
    fun appSensePatientService(props: AppSenseProperties) = 
        AppSensePatientService(props)
    
    @Bean
    fun appSensePatientAgent(service: AppSensePatientService) = 
        AppSensePatientAgent(service)
    
    @Bean
    fun appSenseSchedulerService(props: AppSenseProperties) = 
        AppSenseSchedulerService(props)
    
    @Bean
    fun appSenseSchedulerAgent(
        agentPlatform: AgentPlatform,
        service: AppSenseSchedulerService,
        props: AppSenseProperties
    ) = AppSenseSchedulerAgent(agentPlatform, service, props)
}

fun main(args: Array<String>) {
    runApplication<NewbieClawApplication>(*args)
}
