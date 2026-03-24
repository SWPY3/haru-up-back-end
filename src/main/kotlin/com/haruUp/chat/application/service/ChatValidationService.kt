package com.haruUp.chat.application.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.haruUp.chat.domain.ChatState
import com.haruUp.chat.domain.ValidationResult
import com.haruUp.global.clova.ClovaApiClient
import org.springframework.stereotype.Service
import kotlin.jvm.java

@Service
class ChatValidationService(
    private val clovaApiClient: ClovaApiClient,
    private val objectMapper: ObjectMapper
) {

    /**
     * 학습 동기 검수
     *
     * 현재 ChatState에 저장된
     * category / subCategory / motivation을 바탕으로
     * LLM에게 답변 충분 여부를 판단하게 한다.
     */
    fun validateMotivation(state: ChatState): ValidationResult {
        val prompt = buildValidationPrompt(state)

        val response = clovaApiClient.generateText(
            userMessage = prompt,
            systemMessage = VALIDATION_SYSTEM_PROMPT
        )

        return parseValidationResult(response)
    }

    /**
     * LLM에 전달할 사용자 프롬프트 생성
     *
     * 현재 문맥(category, subCategory, motivation)을 명시적으로 전달하여
     * 답변이 충분한지 판단하도록 유도한다.
     */
    private fun buildValidationPrompt(state: ChatState): String {
        return """
            아래 사용자의 학습 목표 정보가 충분한지 판단하세요.

            상위 관심사: ${state.category}
            세부 관심사: ${state.subCategory}
            동기 답변: ${state.motivation}

            반드시 아래 JSON 형식으로만 답하세요.
            {
              "isValid": true,
              "reason": "판단 이유",
              "followUpQuestion": null
            }

            규칙:
            1. 답변이 구체적이면 isValid=true
            2. 답변이 모호하면 isValid=false
            3. isValid=false면 현재 문맥에 맞는 추가 질문을 followUpQuestion에 작성
            4. JSON 외의 다른 텍스트는 절대 출력하지 마세요
        """.trimIndent()
    }

    /**
     * LLM 응답 문자열을 ValidationResult로 변환
     *
     * 파싱 실패 시에는 안전하게 false 처리하고,
     * 사용자에게 한 번 더 구체적인 설명을 요청한다.
     */
    private fun parseValidationResult(response: String): ValidationResult {
        return try {
            objectMapper.readValue(response, ValidationResult::class.java)
        } catch (e: Exception) {
            ValidationResult(
                isValid = false,
                reason = "LLM 응답 파싱 실패",
                followUpQuestion = "조금 더 구체적으로 설명해주실 수 있을까요?"
            )
        }
    }

    companion object {
        /**
         * LLM 시스템 프롬프트
         *
         * 역할:
         * - 학습 목표 수집용 답변 검수기
         * - 반드시 JSON으로만 응답하게 제한
         */
        private const val VALIDATION_SYSTEM_PROMPT = """
            당신은 학습 목표 수집 챗봇의 답변 검수기입니다.
            사용자의 답변이 충분한지 판단하고 JSON으로만 응답하세요.
        """
    }
}