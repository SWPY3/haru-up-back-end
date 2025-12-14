package com.haruUp.global.config

import com.haruUp.global.logging.HttpClientLoggingInterceptor
import org.springframework.boot.web.client.RestClientCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.BufferingClientHttpRequestFactory
import org.springframework.http.client.SimpleClientHttpRequestFactory

@Configuration
class RestClientConfig {

    @Bean
    fun restClientCustomizer(): RestClientCustomizer {
        return RestClientCustomizer { builder ->
            builder
                .requestFactory(BufferingClientHttpRequestFactory(SimpleClientHttpRequestFactory()))
                .requestInterceptor(HttpClientLoggingInterceptor())
        }
    }
}
