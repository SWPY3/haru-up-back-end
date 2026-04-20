package com.haruUp.chat.application.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.haruUp.chat.domain.ChatState
import com.haruUp.chat.domain.ValidationResult
import com.haruUp.global.clova.ClovaApiClient
import org.springframework.stereotype.Service

@Service
class ChatValidationService(
    private val clovaApiClient: ClovaApiClient,
    private val objectMapper: ObjectMapper
) {

    fun isClearlyNonAnswer(answer: String): Boolean {
        val normalized = normalize(answer)
        if (normalized.isBlank()) return true
        if (NON_ANSWER_KEYWORDS.any { normalized.contains(it) }) return true
        return normalized.length <= 8 && VAGUE_SHORT_KEYWORDS.any { normalized.contains(it) }
    }

    fun isClearlyInvalidSkillLevelAnswer(answer: String): Boolean {
        val normalized = normalize(answer)
        if (normalized.isBlank()) return true
        if (SKILL_LEVEL_CUES.any { normalized.contains(it) }) return false
        return isClearlyNonAnswer(normalized)
    }

    fun isClearlyInvalidRecentExperienceAnswer(answer: String): Boolean {
        val normalized = normalize(answer)
        if (normalized.isBlank()) return true
        if (RECENT_EXPERIENCE_CUES.any { normalized.contains(it) }) return false
        return isClearlyNonAnswer(normalized)
    }

    fun evaluateRecommendationReadiness(state: ChatState): RecommendationReadiness {
        val missing = mutableListOf<String>()

        val goal = state.goal.orEmpty()
        if (goal.isBlank() || isClearlyNonAnswer(goal) || goal.length < 8) {
            missing += "goal"
        }

        val profile = listOf(state.skillLevel, state.recentExperience)
            .filterNotNull()
            .joinToString(" ")
            .trim()
        val profileInvalid = profile.isBlank() ||
            (isClearlyInvalidSkillLevelAnswer(profile) && isClearlyInvalidRecentExperienceAnswer(profile))
        if (profileInvalid) {
            missing += "experience"
        }

        val schedule = listOf(state.dailyAvailableTime, state.targetPeriod)
            .filterNotNull()
            .joinToString(" ")
            .trim()
        if (schedule.isBlank() || !TIME_CUE_REGEX.containsMatchIn(schedule) || !PERIOD_CUE_REGEX.containsMatchIn(schedule)) {
            missing += "schedule"
        }

        return RecommendationReadiness(
            sufficient = missing.isEmpty(),
            missingFields = missing
        )
    }

    fun validateGoal(state: ChatState): ValidationResult {
        val response = clovaApiClient.generateText(
            userMessage = buildValidationPrompt(state),
            systemMessage = VALIDATION_SYSTEM_PROMPT
        )
        return parseValidationResult(response)
    }

    fun validateMotivation(state: ChatState): ValidationResult = validateGoal(state)

    private fun buildValidationPrompt(state: ChatState): String {
        return """
            아래 사용자 목표 정보가 충분한지 판단해 주세요.
            category=${state.category}
            subCategory=${state.subCategory}
            goal=${state.goal}
            profile=${state.skillLevel}
            schedule=${state.dailyAvailableTime}

            반드시 JSON 형식으로만 답변해 주세요.
            {"isValid":true,"reason":"...","followUpQuestion":null}
        """.trimIndent()
    }

    private fun parseValidationResult(response: String): ValidationResult {
        return try {
            objectMapper.readValue(response, ValidationResult::class.java)
        } catch (_: Exception) {
            ValidationResult(
                isValid = false,
                reason = "LLM response parse failed",
                followUpQuestion = "목표를 조금 더 구체적으로 알려주세요."
            )
        }
    }

    private fun normalize(answer: String): String {
        return answer.trim()
            .lowercase()
            .replace("\\s+".toRegex(), " ")
            .replace("[?!.,~]+$".toRegex(), "")
    }

    companion object {
        private val NON_ANSWER_KEYWORDS = listOf(
            "모르겠", "모르겠어", "모르겠습니다",
            "글쎄", "딱히", "아무거나", "그냥",
            "잘 모르", "생각 안", "없어요", "없음"
        )

        private val VAGUE_SHORT_KEYWORDS = listOf(
            "몰라", "모름", "아직", "없어", "없음", "대충"
        )

        private val SKILL_LEVEL_CUES = listOf(
            "입문", "초보", "기초", "중급", "고급", "경험", "해봤", "다뤄봤", "실무", "처음"
        )

        private val RECENT_EXPERIENCE_CUES = listOf(
            "해봤", "만들어", "작성", "설계", "구현", "연동", "프로젝트", "아직 안", "처음"
        )

        private val TIME_CUE_REGEX = Regex("(\\d+)\\s*(시간|분)")
        private val PERIOD_CUE_REGEX = Regex("(\\d+)\\s*(일|주|개월|달|년)")

        private const val VALIDATION_SYSTEM_PROMPT = """
            You are a validator for mission recommendation readiness.
            Return JSON only.
        """
    }
}

data class RecommendationReadiness(
    val sufficient: Boolean,
    val missingFields: List<String>
)
