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

    fun isClearlyNonAnswer(answer: String): Boolean {
        val normalized = normalizeAnswer(answer)

        return isClearlyNonAnswerNormalized(normalized)
    }

    fun isClearlyInvalidSkillLevelAnswer(answer: String): Boolean {
        val normalized = normalizeAnswer(answer)

        if (normalized.isBlank()) {
            return true
        }

        if (SKILL_LEVEL_VALID_PATTERNS.any { pattern -> pattern.containsMatchIn(normalized) }) {
            return false
        }

        return isClearlyNonAnswerNormalized(normalized)
    }

    fun isClearlyInvalidRecentExperienceAnswer(answer: String): Boolean {
        val normalized = normalizeAnswer(answer)

        if (normalized.isBlank()) {
            return true
        }

        if (RECENT_EXPERIENCE_VALID_PATTERNS.any { pattern -> pattern.containsMatchIn(normalized) }) {
            return false
        }

        return isClearlyNonAnswerNormalized(normalized)
    }

    /**
     * 수집된 챗봇 답변 검수
     *
     * 현재 ChatState에 저장된
     * category / subCategory / motivation / goal을 바탕으로
     * LLM에게 답변 충분 여부를 판단하게 한다.
     */
    fun validateGoal(state: ChatState): ValidationResult {
        val prompt = buildValidationPrompt(state)

        val response = clovaApiClient.generateText(
            userMessage = prompt,
            systemMessage = VALIDATION_SYSTEM_PROMPT
        )

        return parseValidationResult(response)
    }

    fun validateMotivation(state: ChatState): ValidationResult = validateGoal(state)

    /**
     * LLM에 전달할 사용자 프롬프트 생성
     *
     * 현재 문맥(category, subCategory, motivation, goal)을 명시적으로 전달하여
     * 답변이 충분한지 판단하도록 유도한다.
     */
    private fun buildValidationPrompt(state: ChatState): String {
        val followUpSection = if (state.followUpAnswer.isNullOrBlank()) {
            ""
        } else {
            "\n보충 답변: ${state.followUpAnswer}"
        }

        return """
            아래 사용자의 학습 목표 정보가 충분한지 판단하세요.

            상위 관심사: ${state.category}
            세부 관심사: ${state.subCategory}
            선택 이유: ${state.motivation}
            현재 목표: ${state.goal}$followUpSection

            반드시 아래 JSON 형식으로만 답하세요.
            {
              "isValid": true,
              "reason": "판단 이유",
              "followUpQuestion": null
            }

            규칙:
            1. 선택 이유와 현재 목표를 함께 보고 판단하세요.
            2. 보충 답변이 있으면 함께 참고하세요.
            3. 이유와 목표가 모두 구체적이면 isValid=true
            4. 목표가 모호하거나 실행 방향이 부족하면 isValid=false
            5. isValid=false면 현재 문맥에 맞는 추가 질문을 followUpQuestion에 작성
            6. JSON 외의 다른 텍스트는 절대 출력하지 마세요
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

    private fun normalizeAnswer(answer: String): String {
        return answer.trim()
            .lowercase()
            .replace("\\s+".toRegex(), " ")
            .replace("[?!.,~]+$".toRegex(), "")
    }

    private fun isClearlyNonAnswerNormalized(normalized: String): Boolean {
        if (normalized.isBlank()) {
            return true
        }

        if (NON_ANSWER_REGEX.matches(normalized)) {
            return true
        }

        return normalized.length <= SHORT_VAGUE_ANSWER_MAX_LENGTH &&
            VAGUE_KEYWORDS.any { keyword -> normalized.contains(keyword) }
    }

    companion object {
        private const val SHORT_VAGUE_ANSWER_MAX_LENGTH = 16

        private val NON_ANSWER_REGEX = Regex(
            "^(글쎄(요)?|몰라(요)?|모르겠(어|어요|는데|네요)?|잘 모르겠(어|어요|는데|네요)?|모르겠음|애매(해|해요|한데)?|막연(해요|하네요|한데)?|딱히(요)?|별로(요)?|없(어|어요|는데)?|없음|전혀 없(어|어요|는데)?|1도 없(어|어요)?|아무거나(요)?|그냥(요)?|그냥 해보고 싶어요|그냥 하고 싶어요|좋아서(요)?|재밌어서(요)?|해보고 싶어서(요)?|관심 없(어|어요|는데)?|생각 안 나(요)?|모르겠지만)$"
        )

        private val VAGUE_KEYWORDS = listOf(
            "글쎄",
            "모르겠",
            "몰라",
            "애매",
            "막연",
            "딱히",
            "별로",
            "없어",
            "없음",
            "전혀 없",
            "1도 없",
            "아무거나",
            "그냥",
            "좋아서",
            "재밌어서",
            "해보고 싶",
            "관심 없",
            "생각 안 나"
        )

        private val SKILL_LEVEL_VALID_PATTERNS = listOf(
            Regex("(^| )(입문|기초|초보|왕초보|초심자|중급|고급)( |$)"),
            Regex("처음(이야|이에요|입니다|이야요|입니다만)?"),
            Regex("해본 ?적(이)? 없"),
            Regex("안 해봤"),
            Regex("안해봤"),
            Regex("경험(이)? 없"),
            Regex("실무 경험(이)? 없"),
            Regex("배운 적(이)? 없"),
            Regex("조금 해봤"),
            Regex("조금 알고"),
            Regex("기본은 알고"),
            Regex("만 해봤"),
            Regex("만 알고")
        )

        private val RECENT_EXPERIENCE_VALID_PATTERNS = listOf(
            Regex("해본 ?적(이)? 없"),
            Regex("안 해봤"),
            Regex("안해봤"),
            Regex("직접 해본 ?건? 없"),
            Regex("아직 없음"),
            Regex("처음(이야|이에요|입니다)?"),
            Regex("해봤"),
            Regex("만들어봤"),
            Regex("작성해봤"),
            Regex("그려봤"),
            Regex("써봤"),
            Regex("정리해봤"),
            Regex("초안"),
            Regex("문서"),
            Regex("화면"),
            Regex("와이어프레임"),
            Regex("피그마"),
            Regex("노션"),
            Regex("기획서")
        )

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
