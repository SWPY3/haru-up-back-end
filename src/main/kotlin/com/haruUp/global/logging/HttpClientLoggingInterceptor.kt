package com.haruUp.global.logging

import org.slf4j.LoggerFactory
import org.springframework.http.HttpRequest
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.http.client.ClientHttpResponse
import org.springframework.util.StreamUtils
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets

class HttpClientLoggingInterceptor : ClientHttpRequestInterceptor {

    private val log = LoggerFactory.getLogger("HTTP_CLIENT_LOG")

    override fun intercept(
        request: HttpRequest,
        body: ByteArray,
        execution: ClientHttpRequestExecution
    ): ClientHttpResponse {
        val startTime = System.currentTimeMillis()

        logRequest(request, body)

        val response = execution.execute(request, body)
        val duration = System.currentTimeMillis() - startTime

        // 응답 바디를 읽기 위해 래핑
        val wrappedResponse = BufferingClientHttpResponseWrapper(response)
        logResponse(wrappedResponse, duration)

        return wrappedResponse
    }

    private fun logRequest(request: HttpRequest, body: ByteArray) {
        val logMessage = buildString {
            append("\n")
            append(">>> HTTP CLIENT REQUEST\n")
            append("  Method: ${request.method}\n")
            append("  URI: ${request.uri}\n")
            append("  Headers: ${sanitizeHeaders(request.headers)}\n")
            if (body.isNotEmpty()) {
                append("  Body: ${truncateBody(String(body, StandardCharsets.UTF_8))}\n")
            }
        }
        log.info(logMessage)
    }

    private fun logResponse(response: BufferingClientHttpResponseWrapper, duration: Long) {
        val body = response.getBodyAsString()
        val logMessage = buildString {
            append("\n")
            append("<<< HTTP CLIENT RESPONSE\n")
            append("  Status: ${response.statusCode}\n")
            append("  Duration: ${duration}ms\n")
            if (body.isNotBlank()) {
                append("  Body: ${truncateBody(body)}\n")
            }
        }

        if (response.statusCode.isError) {
            log.warn(logMessage)
        } else {
            log.info(logMessage)
        }
    }

    private fun sanitizeHeaders(headers: org.springframework.http.HttpHeaders): String {
        val sanitized = headers.toMutableMap()
        // 민감한 헤더 마스킹
        listOf("Authorization", "X-NCP-APIGW-API-KEY", "X-API-KEY").forEach { key ->
            if (sanitized.containsKey(key)) {
                sanitized[key] = listOf("***MASKED***")
            }
        }
        return sanitized.toString()
    }

    private fun truncateBody(body: String, maxLength: Int = 1000): String {
        return if (body.length > maxLength) {
            body.take(maxLength) + "... (truncated)"
        } else body
    }
}

class BufferingClientHttpResponseWrapper(
    private val response: ClientHttpResponse
) : ClientHttpResponse by response {

    private val bodyBytes: ByteArray by lazy {
        StreamUtils.copyToByteArray(response.body)
    }

    override fun getBody(): java.io.InputStream {
        return ByteArrayInputStream(bodyBytes)
    }

    fun getBodyAsString(): String {
        return String(bodyBytes, StandardCharsets.UTF_8)
    }
}
