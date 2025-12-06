package com.haruUp.global.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient
import java.util.UUID

@Configuration
class ClovaApiConfig {

    @Value("\${clova.api.url}")
    private lateinit var clovaApiUrl: String

    @Value("\${clova.api.key}")
    private lateinit var clovaApiKey: String

    @Value("\${clova.api.gateway-key:}")
    private lateinit var clovaApiGatewayKey: String

    @Bean
    fun clovaRestClient(): RestClient {
        val builder = RestClient.builder()
            .baseUrl(clovaApiUrl)
            .defaultHeader("Authorization", "Bearer $clovaApiKey")
            .defaultHeader("Content-Type", "application/json")

        // API Gateway Key가 있으면 추가
        if (clovaApiGatewayKey.isNotBlank()) {
            builder.defaultHeader("X-NCP-APIGW-API-KEY", clovaApiGatewayKey)
        }

        return builder.build()
    }

    @Bean
    fun generateRequestId(): () -> String {
        return { UUID.randomUUID().toString() }
    }
}
