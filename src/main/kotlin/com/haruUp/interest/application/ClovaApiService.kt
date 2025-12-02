package com.haruUp.interest.application

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import java.util.UUID

@Service
class ClovaApiService(
    private val clovaRestClient: RestClient,
    @Value("\${clova.api.model-id:HCX-003}") private val modelId: String
) {

    companion object {
        private val logger = LoggerFactory.getLogger(ClovaApiService::class.java)
    }

    /**
     * Clova API를 호출하여 관심사 추천 받기
     * @param prompt 사용자 프롬프트 (예: "사용자의 관심사를 추천해주세요")
     * @return 추천된 관심사 리스트
     */
    fun getRecommendedInterests(prompt: String): List<String> {
        try {
            val requestId = UUID.randomUUID().toString()
            logger.info("Clova API 호출 시작: requestId={}, prompt={}", requestId, prompt)

            val request = ClovaRequest(
                messages = listOf(
                    Message(role = "system", content = "당신은 사용자의 관심사를 추천하는 AI 어시스턴트입니다. 요청받은 개수만큼 정확히 관심사를 추천해주세요."),
                    Message(role = "user", content = prompt)
                ),
                topP = 0.8,
                topK = 0,
                maxTokens = 256,
                temperature = 0.5,
                repeatPenalty = 5.0,
                stopBefore = emptyList(),
                includeAiFilters = true
            )

            val response = clovaRestClient.post()
                .uri("/testapp/v1/chat-completions/$modelId")
                .header("X-NCP-CLOVASTUDIO-REQUEST-ID", requestId)
                .body(request)
                .retrieve()
                .body(ClovaResponse::class.java)

            logger.info("Clova API 호출 성공: requestId={}, response={}", requestId, response)

            // 응답에서 관심사 키워드 추출
            val content = response?.result?.message?.content
            if (content.isNullOrBlank()) {
                logger.warn("Clova API 응답 내용이 비어있음")
                return emptyList()
            }

            // 쉼표, 줄바꿈, 숫자+점(1. 2. 등) 등으로 구분
            val interests = content
                .split(Regex("[,\n]"))
                .map { it.trim() }
                .map { it.replace(Regex("^\\d+\\.\\s*"), "") } // "1. " 같은 번호 제거
                .filter { it.isNotBlank() }

            logger.info("추출된 관심사: {}", interests)
            return interests

        } catch (e: Exception) {
            logger.error("Clova API 호출 실패", e)
            return emptyList()
        }
    }
}

// Clova API Request DTO
data class ClovaRequest(
    val messages: List<Message>,
    val topP: Double,
    val topK: Int,
    val maxTokens: Int,
    val temperature: Double,
    val repeatPenalty: Double,
    val stopBefore: List<String>,
    val includeAiFilters: Boolean
)

data class Message(
    val role: String,
    val content: String
)

// Clova API Response DTO
data class ClovaResponse(
    val status: Status?,
    val result: Result?
)

data class Status(
    val code: String?,
    val message: String?
)

data class Result(
    val message: MessageContent?,
    val inputLength: Int?,
    val outputLength: Int?,
    val stopReason: String?,
    val seed: Long?,
    val aiFilter: List<AiFilter>?
)

data class MessageContent(
    val role: String?,
    val content: String?
)

data class AiFilter(
    val groupName: String?,
    val name: String?,
    val score: String?,
    val result: String?
)
