package com.haruUp.chat.application.service

import com.haruUp.category.domain.dto.JobDto
import com.haruUp.chat.domain.ChatState
import com.haruUp.missionembedding.dto.MissionDto
import org.springframework.stereotype.Service

@Service
class ChatBotService {

    fun getIntroMessage(): String {
        return """
            0. \uC548\uB155\uD558\uC138\uC694! \uC800\uB294 \uB2F9\uC2E0\uC758 \uD559\uC2B5 \uCF54\uCE58 \uD558\uB8E8\uC608\uC694.
            \uAC00\uC7A5 \uC801\uD569\uD55C \uCEE4\uB9AC\uD058\uB7FC\uC744 \uC124\uACC4\uD558\uAE30 \uC704\uD574
            \uBA87 \uAC00\uC9C0 \uAC04\uB2E8\uD55C \uC9C8\uBB38\uC744 \uB4DC\uB9B4\uAC8C\uC694.

            1\uBC88 \uC9C8\uBB38 : \uBAA9\uD45C
            2\uBC88 \uC9C8\uBB38 : \uB2F9\uC2E0\uC758 \uC2E4\uB825\uC740?
            3\uBC88 \uC9C8\uBB38 : \uC774\uB8E8\uACE0\uC790\uD558\uB294 \uBAA9\uD45C \uAE30\uAC04
            4\uBC88 \uC9C8\uBB38 : \uD558\uB8E8\uC5D0 \uD22C\uC790\uAC00 \uAC00\uB2A5\uD55C \uC2DC\uAC04

            \uBA3C\uC800 1\uBC88 \uC9C8\uBB38\uBD80\uD130 \uB2F5\uD574\uC8FC\uC138\uC694.
            \uC774\uB8E8\uACE0 \uC2F6\uC740 \uBAA9\uD45C\uB97C \uC54C\uB824\uC8FC\uC138\uC694.
        """.trimIndent()
    }

    fun getRestartMessage(): String {
        return """
            \uCC98\uC74C\uBD80\uD130 \uB2E4\uC2DC \uC2DC\uC791\uD560\uAC8C\uC694.

            1\uBC88 \uC9C8\uBB38 : \uBAA9\uD45C
            \uC774\uB8E8\uACE0 \uC2F6\uC740 \uBAA9\uD45C\uB97C \uC54C\uB824\uC8FC\uC138\uC694.
        """.trimIndent()
    }

    fun getSubCategoryQuestion(category: String): String {
        return "$category"
    }

    fun getGoalQuestion(subCategory: String): String {
        return """
            1\uBC88 \uC9C8\uBB38 : \uBAA9\uD45C
            \uC774\uB8E8\uACE0 \uC2F6\uC740 \uBAA9\uD45C\uB97C \uC54C\uB824\uC8FC\uC138\uC694.
        """.trimIndent()
    }

    fun getSkillQuestion(subCategory: String): String {
        return """
            2\uBC88 \uC9C8\uBB38 : \uB2F9\uC2E0\uC758 \uC2E4\uB825\uC740?
            \uD604\uC7AC \uC2E4\uB825 \uC0C1\uD0DC\uB97C \uC54C\uB824\uC8FC\uC138\uC694.
        """.trimIndent()
    }

    fun getTargetPeriodQuestion(): String {
        return """
            3\uBC88 \uC9C8\uBB38 : \uC774\uB8E8\uACE0\uC790\uD558\uB294 \uBAA9\uD45C \uAE30\uAC04
            \uBAA9\uD45C \uAE30\uAC04\uC744 \uC54C\uB824\uC8FC\uC138\uC694. (\uC608: 2\uAC1C\uC6D4, 6\uC8FC)
        """.trimIndent()
    }

    fun getDailyAvailableTimeQuestion(): String {
        return """
            4\uBC88 \uC9C8\uBB38 : \uD558\uB8E8\uC5D0 \uD22C\uC790\uAC00 \uAC00\uB2A5\uD55C \uC2DC\uAC04
            \uD558\uB8E8\uC5D0 \uD22C\uC790\uAC00 \uAC00\uB2A5\uD55C \uC2DC\uAC04\uC744 \uC54C\uB824\uC8FC\uC138\uC694. (\uC608: \uD558\uB8E8 1\uC2DC\uAC04, 40\uBD84)
        """.trimIndent()
    }

    fun getRetryQuestionMessage(field: String, state: ChatState): String {
        return when (field) {
            "goal" -> "\uBAA9\uD45C \uB2F5\uBCC0\uC774 \uC870\uAE08 \uBD80\uC871\uD574\uC694.\n\n" + getGoalQuestion(state.subCategory ?: "N/A")
            "skill" -> "\uC2E4\uB825 \uB2F5\uBCC0\uC774 \uC870\uAE08 \uBD80\uC871\uD574\uC694.\n\n" + getSkillQuestion(state.subCategory ?: "N/A")
            "period" -> "\uBAA9\uD45C \uAE30\uAC04 \uB2F5\uBCC0\uC774 \uC870\uAE08 \uBD80\uC871\uD574\uC694.\n\n" + getTargetPeriodQuestion()
            "dailyTime" -> "\uD558\uB8E8 \uD22C\uC790 \uC2DC\uAC04 \uB2F5\uBCC0\uC774 \uC870\uAE08 \uBD80\uC871\uD574\uC694.\n\n" + getDailyAvailableTimeQuestion()
            else -> "\uC785\uB825 \uC815\uBCF4\uAC00 \uBD80\uC871\uD574\uC694. \uB2E4\uC2DC \uC785\uB825\uD574 \uC8FC\uC138\uC694."
        }
    }

    fun buildRecommendationMessage(
        state: ChatState,
        missions: List<MissionDto>
    ): String {
        val header = buildList {
            add("\uC785\uB825 \uC815\uBCF4\uB97C \uBC14\uD0D5\uC73C\uB85C \uBBF8\uC158\uC744 \uCD94\uCC9C\uD588\uC5B4\uC694.")
            add("")
            add("- \uAD00\uC2EC \uBD84\uC57C(\uC790\uB3D9 \uCD94\uB860): ${state.category}")
            add("- \uC0C1\uC138 \uAD00\uC2EC\uC0AC(\uC790\uB3D9 \uCD94\uB860): ${state.subCategory}")
            add("- \uBAA9\uD45C: ${state.goal}")
            add("- \uC2E4\uB825: ${state.skillLevel}")
            add("- \uBAA9\uD45C \uAE30\uAC04: ${state.targetPeriod}")
            add("- \uD558\uB8E8 \uD22C\uC790 \uAC00\uB2A5 \uC2DC\uAC04: ${state.dailyAvailableTime}")
            add("")
        }

        val missionLines = if (missions.isEmpty()) {
            listOf("\uCD94\uCC9C \uACB0\uACFC\uAC00 \uBE44\uC5B4 \uC788\uC2B5\uB2C8\uB2E4. \uB2F5\uBCC0\uC744 \uC870\uAE08 \uB354 \uAD6C\uCCB4\uC801\uC73C\uB85C \uC785\uB825\uD574 \uC8FC\uC138\uC694.")
        } else {
            missions.mapIndexed { index, mission ->
                "${index + 1}. [\uB09C\uC774\uB3C4 ${mission.difficulty ?: "-"}] ${mission.content}"
            }
        }

        return (header + missionLines).joinToString("\n")
    }

    fun isValidCategory(answer: String, options: List<JobDto>): Boolean {
        val answerId = answer.toLongOrNull() ?: return false
        return options.any { it.id == answerId }
    }

    fun isValidSubCategory(answer: String, options: List<String>): Boolean {
        return options.contains(answer)
    }

    fun resetState(state: ChatState) {
        state.depth = 1
        state.categoryNo = null
        state.category = null
        state.subCategoryNo = null
        state.subCategory = null
        state.motivation = null
        state.goal = null
        state.desiredOutcome = null
        state.skillLevel = null
        state.recentExperience = null
        state.targetPeriod = null
        state.dailyAvailableTime = null
        state.additionalOpinion = null
        state.needFollowUp = false
        state.followUpQuestion = null
        state.followUpAnswer = null
    }
}
