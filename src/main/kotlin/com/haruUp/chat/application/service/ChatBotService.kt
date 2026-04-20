package com.haruUp.chat.application.service

import com.haruUp.category.domain.dto.JobDto
import com.haruUp.chat.domain.ChatState
import com.haruUp.missionembedding.dto.MissionDto
import org.springframework.stereotype.Service

@Service
class ChatBotService {

    fun getIntroMessage(): String {
        return """
            안녕하세요. 미션 추천 챗봇입니다.
            관심사 선택 후 고정 질문 3개만 답하면 추천 가능 여부를 바로 판단해드릴게요.

            어떤 관심 분야를 선택하시겠어요?
            아래 목록에서 번호를 입력해 주세요.
        """.trimIndent()
    }

    fun getRestartMessage(): String {
        return "처음부터 다시 시작할게요. 관심 분야를 번호로 선택해 주세요."
    }

    fun getSubCategoryQuestion(category: String): String {
        return "${category}에서 어떤 상세 관심사를 원하시나요? 번호로 선택해 주세요."
    }

    fun getGoalQuestion(subCategory: String): String {
        return "${subCategory} 기준으로 이번에 달성하고 싶은 목표/결과물을 알려주세요."
    }

    fun getProfileQuestion(subCategory: String): String {
        return "${subCategory} 관련 현재 수준과 최근 경험을 함께 알려주세요."
    }

    fun getScheduleQuestion(): String {
        return "하루 투자 가능 시간과 목표 기간을 알려주세요. (예: 하루 1시간, 2개월)"
    }

    fun getSupplementQuestion(missingFields: List<String>): String {
        if (missingFields.isEmpty()) {
            return "추천에 필요한 정보가 부족합니다. 목표, 현재 수준/경험, 시간/기간 정보를 조금 더 구체적으로 알려주세요."
        }

        val labels = missingFields.joinToString(", ") { field ->
            when (field) {
                "goal" -> "목표/결과물"
                "experience" -> "현재 수준/최근 경험"
                "schedule" -> "시간/기간"
                else -> field
            }
        }
        return "추천 전에 정보가 조금 부족해요. 다음 항목을 보완해 주세요: $labels"
    }

    fun buildRecommendationMessage(
        state: ChatState,
        missions: List<MissionDto>
    ): String {
        val header = buildList {
            add("추천 준비가 완료되어 미션을 생성했어요.")
            add("")
            add("- 관심 분야: ${state.category}")
            add("- 상세 관심사: ${state.subCategory}")
            add("- 목표/결과물: ${state.goal}")
            add("- 현재 수준/최근 경험: ${state.skillLevel}")
            add("- 시간/기간: ${state.dailyAvailableTime} / ${state.targetPeriod}")
            if (!state.additionalOpinion.isNullOrBlank()) {
                add("- 추가 의견: ${state.additionalOpinion}")
            }
            add("")
        }

        val missionLines = if (missions.isEmpty()) {
            listOf("현재 조건으로 생성된 미션이 없습니다. 답변을 조금 더 구체적으로 입력해 주세요.")
        } else {
            missions.mapIndexed { index, mission ->
                "${index + 1}. [난이도 ${mission.difficulty ?: "-"}] ${mission.content}"
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
