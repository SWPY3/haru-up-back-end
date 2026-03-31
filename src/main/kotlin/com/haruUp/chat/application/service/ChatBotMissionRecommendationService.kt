package com.haruUp.chat.application.service

import com.haruUp.global.clova.MissionMemberProfile
import com.haruUp.missionembedding.dto.MissionDto
import com.haruUp.missionembedding.service.MissionRecommendationService
import org.springframework.stereotype.Service

@Service
class ChatBotMissionRecommendationService(
    private val missionRecommendationService: MissionRecommendationService
) {

    suspend fun recommend(context: ChatBotMissionContext): List<MissionDto> {
        if (
            context.category.isBlank() ||
            context.subCategory.isBlank() ||
            context.goal.isBlank() ||
            context.desiredOutcome.isBlank() ||
            context.skillLevel.isBlank() ||
            context.recentExperience.isBlank() ||
            context.targetPeriod.isBlank() ||
            context.dailyAvailableTime.isBlank()
        ) {
            return emptyList()
        }

        val additionalContext = buildAdditionalContext(context)
        val excludeContents = context.completedMissions.map { it.content }
        val memberProfile = MissionMemberProfile(
            jobName = context.category,
            jobDetailName = context.subCategory
        )

        return missionRecommendationService.recommendTodayMissions(
            directFullPath = listOf(context.category, context.subCategory),
            memberProfile = memberProfile,
            excludeContents = excludeContents,
            additionalContext = additionalContext
        )
    }

    private fun buildAdditionalContext(context: ChatBotMissionContext): String {
        val lines = mutableListOf(
            "현재 목표: ${context.goal}",
            "최종 결과물: ${context.desiredOutcome}",
            "현재 실력: ${context.skillLevel}",
            "최근 직접 해본 작업: ${context.recentExperience}",
            "목표 기간: ${context.targetPeriod}",
            "하루 투자 가능 시간: ${context.dailyAvailableTime}",
            "미션 원칙: 하루에 한 번 끝낼 수 있는 단일 작업만 추천"
        )

        extractDailyTimeBudgetMinutes(context.dailyAvailableTime)?.let {
            lines.add("하루 미션 시간 상한: 약 ${it}분 이내")
        }

        if (!context.additionalOpinion.isNullOrBlank()) {
            lines.add("추가 의견: ${context.additionalOpinion}")
        }

        if (context.completedMissions.isNotEmpty()) {
            lines.add("이번 추천 모드: 다음날 후속 추천")
            lines.add("전날 완료한 미션:")
            context.completedMissions
                .sortedBy { it.difficulty }
                .forEach { mission ->
                    lines.add("- 난이도 ${mission.difficulty}: ${mission.content}")
                }
            lines.add("위 미션은 모두 완료된 상태입니다.")
            lines.add("같은 난이도라도 전날 미션보다 한 단계 더 진전된 후속 미션으로 추천하세요.")
            lines.add("전날과 동일하거나 거의 같은 미션은 다시 추천하지 마세요.")
            lines.add("전날보다 더 적용형, 더 실전형, 더 구체적인 결과물이 나오도록 추천하세요.")
        }

        return lines.joinToString("\n")
    }

    private fun extractDailyTimeBudgetMinutes(input: String): Int? {
        val hourMinutePattern = Regex("(\\d+)\\s*시간(?:\\s*(\\d+)\\s*분)?")
        val standaloneMinutePattern = Regex("(\\d+)\\s*분")

        val totals = mutableListOf<Int>()

        hourMinutePattern.findAll(input).forEach { match ->
            val hours = match.groupValues[1].toIntOrNull() ?: 0
            val minutes = match.groupValues[2].toIntOrNull() ?: 0
            totals += hours * 60 + minutes
        }

        if (totals.isEmpty()) {
            standaloneMinutePattern.findAll(input).forEach { match ->
                match.groupValues[1].toIntOrNull()?.let { totals += it }
            }
        }

        return totals.minOrNull()
    }
}

data class ChatBotMissionContext(
    val category: String,
    val subCategory: String,
    val goal: String,
    val desiredOutcome: String,
    val skillLevel: String,
    val recentExperience: String,
    val targetPeriod: String,
    val dailyAvailableTime: String,
    val additionalOpinion: String? = null,
    val completedMissions: List<ChatBotCompletedMission> = emptyList()
)

data class ChatBotCompletedMission(
    val content: String,
    val difficulty: Int
)
