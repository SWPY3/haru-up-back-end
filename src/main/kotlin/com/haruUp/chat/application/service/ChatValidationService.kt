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

    fun isClearlyInvalidTargetPeriod(answer: String): Boolean {
        val normalized = normalize(answer)
        if (isClearlyNonAnswer(normalized)) return true
        return !PERIOD_CUE_REGEX.containsMatchIn(normalized)
    }

    fun isClearlyInvalidDailyTime(answer: String): Boolean {
        val normalized = normalize(answer)
        if (isClearlyNonAnswer(normalized)) return true
        return !TIME_CUE_REGEX.containsMatchIn(normalized)
    }

    fun evaluateRecommendationReadiness(state: ChatState): RecommendationReadiness {
        val missing = mutableListOf<String>()

        val goal = state.goal.orEmpty()
        if (goal.isBlank() || isClearlyNonAnswer(goal) || goal.length < 5) {
            missing += "goal"
        }

        val skill = state.skillLevel.orEmpty()
        if (skill.isBlank() || isClearlyInvalidSkillLevelAnswer(skill)) {
            missing += "skill"
        }

        val period = state.targetPeriod.orEmpty()
        if (period.isBlank() || isClearlyInvalidTargetPeriod(period)) {
            missing += "period"
        }

        val dailyTime = state.dailyAvailableTime.orEmpty()
        if (dailyTime.isBlank() || isClearlyInvalidDailyTime(dailyTime)) {
            missing += "dailyTime"
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
            skill=${state.skillLevel}
            targetPeriod=${state.targetPeriod}
            dailyTime=${state.dailyAvailableTime}

            반드시 JSON 형식으로만 응답해 주세요.
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
                followUpQuestion = "\uBAA9\uD45C\uB97C \uC870\uAE08 \uB354 \uAD6C\uCCB4\uC801\uC73C\uB85C \uC54C\uB824\uC8FC\uC138\uC694."
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
            "\uBAA8\uB974\uACA0\uC5B4",
            "\uBAA8\uB974\uACA0\uC2B5\uB2C8\uB2E4",
            "\uAE00\uC138",
            "\uB313\uC787",
            "\uC544\uBB34\uAC70\uB098",
            "\uADF8\uB0E5",
            "\uC798 \uBAA8\uB984",
            "\uC0DD\uAC01 \uC5C6\uC74C"
        )

        private val VAGUE_SHORT_KEYWORDS = listOf(
            "\uBAA8\uB974",
            "\uC544\uC9C1",
            "\uC5C6\uC5B4",
            "\uC5C6\uC74C",
            "\uB300\uCDA9"
        )

        private val SKILL_LEVEL_CUES = listOf(
            "\uC785\uBB38",
            "\uCD08\uBCF4",
            "\uAE30\uCD08",
            "\uC911\uAE09",
            "\uACE0\uAE09",
            "\uACBD\uD5D8",
            "\uC2E0\uC785",
            "\uCC98\uC74C",
            "\uC2E4\uBB34"
        )

        private val RECENT_EXPERIENCE_CUES = listOf(
            "\uD574\uBD24",
            "\uB9CC\uB4E4",
            "\uC791\uC131",
            "\uC124\uACC4",
            "\uAD6C\uD604",
            "\uD504\uB85C\uC81D\uD2B8",
            "\uCC98\uC74C",
            "\uC5C6\uC74C"
        )

        private val TIME_CUE_REGEX = Regex("(\\d+)\\s*(\uC2DC\uAC04|\uBD84)")
        private val PERIOD_CUE_REGEX = Regex("(\\d+)\\s*(\uC77C|\uC8FC|\uAC1C\uC6D4|\uB2EC)")

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
