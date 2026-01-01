package com.haruUp.global.logging

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.util.ContentCachingRequestWrapper
import org.springframework.web.util.ContentCachingResponseWrapper
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Component
class ApiLoggingFilter(
    private val objectMapper: ObjectMapper
) : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger("API_LOG")
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        // 🔥 SSE 요청은 절대 감싸지 말 것
        if (isSseRequest(request) || shouldSkip(request)) {
            filterChain.doFilter(request, response)
            return
        }

        val wrappedRequest = ContentCachingRequestWrapper(request)
        val wrappedResponse = ContentCachingResponseWrapper(response)

        val startTime = System.currentTimeMillis()

        try {
            filterChain.doFilter(wrappedRequest, wrappedResponse)
        } finally {
            val duration = System.currentTimeMillis() - startTime
            wrappedResponse.setHeader("X-Response-Time", "${duration}ms")
            logApiCall(wrappedRequest, wrappedResponse, duration)
            wrappedResponse.copyBodyToResponse()
        }
    }

    private fun isSseRequest(request: HttpServletRequest): Boolean {
        return request.getHeader("Accept") == "text/event-stream" ||
                request.requestURI.startsWith("/api/member/curation")
    }

    private fun shouldSkip(request: HttpServletRequest): Boolean {
        val uri = request.requestURI
        return uri.startsWith("/actuator") ||
                uri.startsWith("/swagger") ||
                uri.startsWith("/v3/api-docs") ||
                uri.startsWith("/favicon.ico") ||
                uri == "/health"
    }

    private fun logApiCall(
        request: ContentCachingRequestWrapper,
        response: ContentCachingResponseWrapper,
        duration: Long
    ) {
        val logData = mapOf(
            "timestamp" to LocalDateTime.now().format(dateFormatter),
            "method" to request.method,
            "uri" to request.requestURI,
            "query" to (request.queryString ?: ""),
            "clientIp" to getClientIp(request),
            "status" to response.status,
            "duration" to duration,
            "requestBody" to parseJsonOrTruncate(getRequestBody(request)),
            "responseBody" to parseJsonOrTruncate(getResponseBody(response))
        )

        val jsonLog = objectMapper.writeValueAsString(logData)

        if (response.status >= 400) {
            log.warn(jsonLog)
        } else {
            log.info(jsonLog)
        }
    }

    private fun getClientIp(request: HttpServletRequest): String {
        val xForwardedFor = request.getHeader("X-Forwarded-For")
        return if (!xForwardedFor.isNullOrBlank()) {
            xForwardedFor.split(",").first().trim()
        } else {
            request.remoteAddr
        }
    }

    private fun getRequestBody(request: ContentCachingRequestWrapper): String {
        val content = request.contentAsByteArray
        return if (content.isNotEmpty()) {
            String(content, StandardCharsets.UTF_8)
        } else ""
    }

    private fun getResponseBody(response: ContentCachingResponseWrapper): String {
        val content = response.contentAsByteArray
        return if (content.isNotEmpty()) {
            String(content, StandardCharsets.UTF_8)
        } else ""
    }

    private fun parseJsonOrTruncate(body: String, maxLength: Int = 5000): Any {
        if (body.isBlank()) return ""
        return try {
            objectMapper.readTree(body)
        } catch (e: Exception) {
            if (body.length > maxLength) {
                body.take(maxLength) + "...(truncated)"
            } else body
        }
    }
}