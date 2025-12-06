package com.haruUp.global.clova

import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

@Component
class ClovaApiClient(
    private val clovaRestClient: RestClient,
    private val generateRequestId: () -> String
) {

    /**
     * Clova API에 채팅 완성 요청을 보냅니다.
     *
     * @param messages 대화 메시지 리스트
     * @param maxTokens 최대 토큰 수 (기본값: 1000)
     * @param temperature 온도 파라미터 (기본값: 0.5)
     * @param topK Top-K 샘플링 파라미터 (기본값: 0)
     * @param topP Top-P 샘플링 파라미터 (기본값: 0.8)
     * @param repeatPenalty 반복 패널티 (기본값: 1.0)
     * @param stopBefore 중단 전 시퀀스 리스트
     * @param includeAiFilters AI 필터 포함 여부 (기본값: true)
     * @return Clova API 응답
     */
    fun chatCompletion(
        messages: List<ChatMessage>,
        maxTokens: Int = 1000,
        temperature: Double = 0.5,
        topK: Int = 0,
        topP: Double = 0.8,
        repeatPenalty: Double = 1.0,
        stopBefore: List<String> = emptyList(),
        includeAiFilters: Boolean = true
    ): ClovaApiResponse {
        val request = ClovaApiRequest(
            messages = messages,
            topP = topP,
            topK = topK,
            maxTokens = maxTokens,
            temperature = temperature,
            repeatPenalty = repeatPenalty,
            stopBefore = stopBefore,
            includeAiFilters = includeAiFilters
        )

        return clovaRestClient.post()
            .uri("/testapp/v1/chat-completions/HCX-003")
            .header("X-NCP-CLOVASTUDIO-REQUEST-ID", generateRequestId())
            .body(request)
            .retrieve()
            .body<ClovaApiResponse>()
            ?: throw RuntimeException("Clova API 응답이 null입니다.")
    }

    /**
     * 간단한 텍스트 생성 요청
     *
     * @param userMessage 사용자 메시지
     * @param systemMessage 시스템 메시지 (선택)
     * @return 생성된 텍스트
     */
    fun generateText(
        userMessage: String,
        systemMessage: String? = null
    ): String {
        val messages = mutableListOf<ChatMessage>()

        systemMessage?.let {
            messages.add(ChatMessage(role = "system", content = it))
        }

        messages.add(ChatMessage(role = "user", content = userMessage))

        val response = chatCompletion(messages)
        return response.result?.message?.content ?: throw RuntimeException("Clova API 응답에 content가 없습니다.")
    }
}

data class ChatMessage(
    val role: String,
    val content: String
)

data class ClovaApiRequest(
    val messages: List<ChatMessage>,
    val topP: Double,
    val topK: Int,
    val maxTokens: Int,
    val temperature: Double,
    val repeatPenalty: Double,
    val stopBefore: List<String>,
    val includeAiFilters: Boolean
)

data class ClovaApiResponse(
    val status: Status?,
    val result: Result?
)

data class Status(
    val code: String,
    val message: String
)

data class Result(
    val message: Message,
    val stopReason: String,
    val inputLength: Int,
    val outputLength: Int,
    val aiFilter: List<AiFilter>?
)

data class Message(
    val role: String,
    val content: String
)

data class AiFilter(
    val groupName: String,
    val name: String,
    val score: String,
    val result: String
)
