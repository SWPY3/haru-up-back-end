package com.haruUp.chat.application

import com.haruUp.category.application.JobDetailService
import com.haruUp.category.application.JobService
import com.haruUp.category.domain.dto.JobDetailDto
import com.haruUp.category.domain.dto.JobDto
import com.haruUp.chat.application.service.ChatBotMissionContext
import com.haruUp.chat.application.service.ChatBotMissionRecommendationService
import com.haruUp.chat.application.service.ChatBotService
import com.haruUp.chat.application.service.ChatValidationService
import com.haruUp.chat.domain.ChatRequest
import com.haruUp.chat.domain.ChatResponse
import com.haruUp.chat.domain.ChatState
import com.haruUp.chat.repository.ChatRedisRepository
import kotlinx.coroutines.runBlocking
import org.springframework.stereotype.Component

@Component
class ChatBotUseCase(
    private var jobService: JobService,
    private var jobDetailService: JobDetailService,
    private var chatBotService: ChatBotService,
    private var chatValidationService: ChatValidationService,
    private var chatRedisRepository: ChatRedisRepository,
    private var chatBotMissionRecommendationService: ChatBotMissionRecommendationService
) {

    fun chatWithBot(request: ChatRequest): ChatResponse {
        val state = chatRedisRepository.findBySessionId(request.sessionId) ?: ChatState()
        val content = request.content.trim()

        val response = when {
            content.lowercase() in RESET_COMMANDS -> handleReset(state)
            state.depth == 0 -> handleIntro(state)
            state.depth == 1 -> handleGoal(content, state)
            state.depth == 2 -> handleSkill(content, state)
            state.depth == 3 -> handleTargetPeriod(content, state)
            state.depth == 4 -> handleDailyAvailableTime(content, state)
            else -> handleReset(state)
        }

        if (response.completed) {
            chatRedisRepository.deleteBySessionId(request.sessionId)
        } else {
            chatRedisRepository.saveChatState(request.sessionId, state)
        }
        return response
    }

    private fun handleIntro(state: ChatState): ChatResponse {
        state.depth = 1
        return ChatResponse(
            message = chatBotService.getIntroMessage(),
            nextDepth = 1,
            completed = false,
            options = emptyList()
        )
    }

    private fun handleGoal(content: String, state: ChatState): ChatResponse {
        if (content.isBlank() || chatValidationService.isClearlyNonAnswer(content)) {
            return ChatResponse(
                message = chatBotService.getGoalQuestion(state.subCategory ?: "N/A"),
                nextDepth = 1,
                completed = false,
                options = emptyList()
            )
        }

        state.goal = content
        state.desiredOutcome = content
        state.depth = 2

        return ChatResponse(
            message = chatBotService.getSkillQuestion(state.subCategory ?: "N/A"),
            nextDepth = 2,
            completed = false,
            options = emptyList()
        )
    }

    private fun handleSkill(content: String, state: ChatState): ChatResponse {
        if (content.isBlank() || chatValidationService.isClearlyInvalidSkillLevelAnswer(content)) {
            return ChatResponse(
                message = chatBotService.getSkillQuestion(state.subCategory ?: "N/A"),
                nextDepth = 2,
                completed = false,
                options = emptyList()
            )
        }

        state.skillLevel = content
        state.recentExperience = content
        state.depth = 3

        return ChatResponse(
            message = chatBotService.getTargetPeriodQuestion(),
            nextDepth = 3,
            completed = false,
            options = emptyList()
        )
    }

    private fun handleTargetPeriod(content: String, state: ChatState): ChatResponse {
        if (content.isBlank() || chatValidationService.isClearlyInvalidTargetPeriod(content)) {
            return ChatResponse(
                message = chatBotService.getTargetPeriodQuestion(),
                nextDepth = 3,
                completed = false,
                options = emptyList()
            )
        }

        state.targetPeriod = content
        state.depth = 4

        return ChatResponse(
            message = chatBotService.getDailyAvailableTimeQuestion(),
            nextDepth = 4,
            completed = false,
            options = emptyList()
        )
    }

    private fun handleDailyAvailableTime(content: String, state: ChatState): ChatResponse {
        if (content.isBlank() || chatValidationService.isClearlyInvalidDailyTime(content)) {
            return ChatResponse(
                message = chatBotService.getDailyAvailableTimeQuestion(),
                nextDepth = 4,
                completed = false,
                options = emptyList()
            )
        }

        state.dailyAvailableTime = content

        val readiness = chatValidationService.evaluateRecommendationReadiness(state)
        if (readiness.sufficient) {
            return finalizeWithRecommendation(state)
        }

        return moveToMissingQuestion(state, readiness.missingFields)
    }

    private fun moveToMissingQuestion(state: ChatState, missingFields: List<String>): ChatResponse {
        val nextField = when {
            "goal" in missingFields -> "goal"
            "skill" in missingFields -> "skill"
            "period" in missingFields -> "period"
            "dailyTime" in missingFields -> "dailyTime"
            else -> "goal"
        }

        val nextDepth = when (nextField) {
            "goal" -> 1
            "skill" -> 2
            "period" -> 3
            "dailyTime" -> 4
            else -> 1
        }
        state.depth = nextDepth

        return ChatResponse(
            message = chatBotService.getRetryQuestionMessage(nextField, state),
            nextDepth = nextDepth,
            completed = false,
            options = emptyList()
        )
    }

    private fun finalizeWithRecommendation(state: ChatState): ChatResponse {
        inferInterestFromAnswers(state)

        val missions = runBlocking {
            chatBotMissionRecommendationService.recommend(
                ChatBotMissionContext(
                    category = state.category.orEmpty(),
                    subCategory = state.subCategory.orEmpty(),
                    goal = state.goal.orEmpty(),
                    desiredOutcome = state.desiredOutcome ?: state.goal.orEmpty(),
                    skillLevel = state.skillLevel.orEmpty(),
                    recentExperience = state.recentExperience ?: state.skillLevel.orEmpty(),
                    targetPeriod = state.targetPeriod.orEmpty(),
                    dailyAvailableTime = state.dailyAvailableTime.orEmpty(),
                    additionalOpinion = state.additionalOpinion
                )
            )
        }

        state.depth = 999
        return ChatResponse(
            message = chatBotService.buildRecommendationMessage(state, missions),
            nextDepth = 999,
            completed = true,
            options = emptyList()
        )
    }

    private fun inferInterestFromAnswers(state: ChatState) {
        if (!state.category.isNullOrBlank() && !state.subCategory.isNullOrBlank()) {
            return
        }

        val text = listOfNotNull(
            state.goal,
            state.skillLevel,
            state.targetPeriod,
            state.dailyAvailableTime
        ).joinToString(" ").lowercase()

        val jobs = jobService.getJobList()
        if (jobs.isEmpty()) {
            state.category = state.category ?: "\uC9C1\uBB34 \uAD00\uC2EC\uC0AC \uC77C\uBC18 \uAC1C\uBC1C"
            state.subCategory = state.subCategory ?: "\uAE30\uCD08 \uC6F9 \uAC1C\uBC1C"
            return
        }

        var bestJob: JobDto? = null
        var bestDetail: JobDetailDto? = null
        var bestScore = Int.MIN_VALUE

        for (job in jobs) {
            val details = runCatching { jobDetailService.getJobDetailList(job.id) }.getOrDefault(emptyList())
            if (details.isEmpty()) {
                val score = scoreText(text, listOf(job.jobName))
                if (score > bestScore) {
                    bestScore = score
                    bestJob = job
                    bestDetail = null
                }
                continue
            }

            for (detail in details) {
                val score = scoreText(text, listOf(job.jobName, detail.jobDetailName))
                if (score > bestScore) {
                    bestScore = score
                    bestJob = job
                    bestDetail = detail
                }
            }
        }

        val fallbackJob = bestJob ?: jobs.first()
        val fallbackDetail = bestDetail ?: runCatching {
            jobDetailService.getJobDetailList(fallbackJob.id).firstOrNull()
        }.getOrNull()

        state.categoryNo = fallbackJob.id
        state.category = fallbackJob.jobName
        state.subCategoryNo = fallbackDetail?.id
        state.subCategory = fallbackDetail?.jobDetailName ?: fallbackJob.jobName
    }

    private fun scoreText(text: String, terms: List<String>): Int {
        var score = 0
        for (raw in terms) {
            val term = raw.lowercase()
            if (term.isBlank()) continue
            if (text.contains(term)) {
                score += 3
            }
            tokenize(term).forEach { token ->
                if (token.length >= 2 && text.contains(token)) {
                    score += 1
                }
            }
        }
        return score
    }

    private fun tokenize(value: String): List<String> {
        return value.split(" ", "/", ",", "(", ")", "-", "_")
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    private fun handleReset(state: ChatState): ChatResponse {
        chatBotService.resetState(state)
        return ChatResponse(
            message = chatBotService.getRestartMessage(),
            nextDepth = 1,
            completed = false,
            options = emptyList()
        )
    }

    companion object {
        private val RESET_COMMANDS = setOf(
            "reset",
            "restart",
            "\uCC98\uC74C\uBD80\uD130",
            "\uB2E4\uC2DC"
        )
    }
}
