package com.haruUp.global.clova

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

@Component
class ClovaApiClient(
    private val clovaRestClient: RestClient,
    private val generateRequestId: () -> String
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        const val MODEL_HCX_007 = "HCX-007"
        const val MODEL_HCX_003 = "HCX-003"
    }

    /**
     * Clova API에 채팅 완성 요청을 보냅니다.
     * @param model 사용할 모델 (HCX-007, HCX-003 등)
     */
    fun chatCompletion(
        messages: List<ChatMessage>,
        model: String = MODEL_HCX_007,
        maxTokens: Int = 2048,
        temperature: Double = 0.5,
        topK: Int = 0,
        topP: Double = 0.8,
        repeatPenalty: Double = 1.1,
        seed: Int? = null
    ): ClovaApiResponse {
        // HCX-007은 thinking 모드 지원, HCX-003은 미지원
        val useThinking = model == MODEL_HCX_007

        val request = if (useThinking) {
            ClovaRequestWithThinking(
                messages = messages,
                topP = topP,
                topK = topK,
                maxCompletionTokens = maxTokens,
                temperature = temperature,
                repetitionPenalty = repeatPenalty,
                seed = seed ?: 0,
                thinking = ThinkingConfig()
            )
        } else {
            ClovaRequestBasic(
                messages = messages,
                topP = topP,
                topK = topK,
                maxTokens = maxTokens,
                temperature = temperature,
                repeatPenalty = repeatPenalty,
                seed = seed ?: 0
            )
        }

        // HCX-007은 v3, HCX-003은 v1 API 사용
        val apiVersion = if (model == MODEL_HCX_007) "v3" else "v1"

        logger.debug("$model API 요청: messages=${messages.size}개, temp=$temperature, apiVersion=$apiVersion")

        val response = clovaRestClient.post()
            .uri("/$apiVersion/chat-completions/$model")
            .header("X-NCP-CLOVASTUDIO-REQUEST-ID", generateRequestId())
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON)
            .body(request)
            .retrieve()
            .body(ClovaApiResponse::class.java)
            ?: throw ClovaApiException("Clova API 응답이 null입니다.")

        logger.debug("$model 응답: content length = ${response.result?.message?.content?.length ?: 0}")
        return response
    }

    /**
     * 간단한 텍스트 생성 요청
     * @param model 사용할 모델 (기본값: HCX-007)
     */
    fun generateText(
        userMessage: String,
        systemMessage: String? = null,
        model: String = MODEL_HCX_007,
        temperature: Double = 0.5,
        seed: Int? = null
    ): String {
        val messages = buildList {
            systemMessage?.let { add(ChatMessage(role = "system", content = it)) }
            add(ChatMessage(role = "user", content = userMessage))
        }

        val response = chatCompletion(
            messages = messages,
            model = model,
            temperature = temperature,
            seed = seed
        )

        return response.result?.message?.content
            ?: throw ClovaApiException("Clova API 응답에 content가 없습니다.")
    }
}

// Request DTOs
// HCX-007용 (thinking 모드 지원)
data class ClovaRequestWithThinking(
    val messages: List<ChatMessage>,
    val topP: Double,
    val topK: Int,
    val maxCompletionTokens: Int,
    val temperature: Double,
    val repetitionPenalty: Double,
    val seed: Int,
    val thinking: ThinkingConfig
)

// HCX-003용 (기본 모드)
data class ClovaRequestBasic(
    val messages: List<ChatMessage>,
    val topP: Double,
    val topK: Int,
    val maxTokens: Int,
    val temperature: Double,
    val repeatPenalty: Double,
    val seed: Int
)

data class ThinkingConfig(
    val effort: String = "low"
)

data class ChatMessage(
    val role: String,
    val content: String
)

// Response DTOs
@JsonIgnoreProperties(ignoreUnknown = true)
data class ClovaApiResponse(
    val status: ClovaStatus?,
    val result: ClovaResult?
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ClovaStatus(
    val code: String,
    val message: String
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ClovaResult(
    val message: ClovaMessage,
    val stopReason: String? = null,
    val inputLength: Int? = null,
    val outputLength: Int? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ClovaMessage(
    val role: String,
    val content: String
)

// Exception
class ClovaApiException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
