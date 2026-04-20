package com.haruUp.chat.application

import com.haruUp.category.application.JobDetailService
import com.haruUp.category.application.JobService
import com.haruUp.chat.application.service.ChatBotMissionContext
import com.haruUp.chat.application.service.ChatBotMissionRecommendationService
import com.haruUp.chat.application.service.ChatBotService
import com.haruUp.chat.application.service.ChatValidationService
import com.haruUp.chat.domain.ChatOption
import com.haruUp.chat.domain.ChatOptionType
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
            state.depth == 1 -> handleCategory(content, state)
            state.depth == 2 -> handleSubCategory(content, state)
            state.depth == 3 -> handleGoal(content, state)
            state.depth == 4 -> handleProfile(content, state)
            state.depth == 5 -> handleSchedule(content, state)
            state.depth == 6 -> handleSupplement(content, state)
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
            options = buildJobOptions()
        )
    }

    private fun handleCategory(content: String, state: ChatState): ChatResponse {
        val options = jobService.getJobList()

        if (content.isBlank()) {
            return ChatResponse(
                message = "관심 분야를 번호로 선택해 주세요.",
                nextDepth = 1,
                completed = false,
                options = buildJobOptions()
            )
        }

        val selectedNo = content.toLongOrNull()
            ?: return ChatResponse(
                message = "번호로만 선택해 주세요.",
                nextDepth = 1,
                completed = false,
                options = buildJobOptions()
            )

        val selected = options.find { it.id == selectedNo }
            ?: return ChatResponse(
                message = "목록에 없는 번호입니다. 다시 선택해 주세요.",
                nextDepth = 1,
                completed = false,
                options = buildJobOptions()
            )

        state.categoryNo = selected.id
        state.category = selected.jobName
        state.depth = 2

        return ChatResponse(
            message = chatBotService.getSubCategoryQuestion(selected.jobName),
            nextDepth = 2,
            completed = false,
            options = buildJobDetailOptions(selected.id)
        )
    }

    private fun handleSubCategory(content: String, state: ChatState): ChatResponse {
        val categoryNo = state.categoryNo
            ?: return handleReset(state)
        val category = state.category
            ?: return handleReset(state)
        val options = jobDetailService.getJobDetailList(categoryNo)

        if (content.isBlank()) {
            return ChatResponse(
                message = "상세 관심사를 번호로 선택해 주세요.",
                nextDepth = 2,
                completed = false,
                options = buildJobDetailOptions(categoryNo)
            )
        }

        val selectedNo = content.toLongOrNull()
            ?: return ChatResponse(
                message = "번호로만 선택해 주세요.",
                nextDepth = 2,
                completed = false,
                options = buildJobDetailOptions(categoryNo)
            )

        val selected = options.find { it.id == selectedNo }
            ?: return ChatResponse(
                message = "목록에 없는 번호입니다. 다시 선택해 주세요.",
                nextDepth = 2,
                completed = false,
                options = buildJobDetailOptions(categoryNo)
            )

        state.subCategoryNo = selected.id
        state.subCategory = selected.jobDetailName
        state.depth = 3

        return ChatResponse(
            message = chatBotService.getGoalQuestion(state.subCategory ?: category),
            nextDepth = 3,
            completed = false,
            options = emptyList()
        )
    }

    private fun handleGoal(content: String, state: ChatState): ChatResponse {
        if (content.isBlank() || chatValidationService.isClearlyNonAnswer(content)) {
            return ChatResponse(
                message = chatBotService.getGoalQuestion(state.subCategory ?: "선택한 분야"),
                nextDepth = 3,
                completed = false,
                options = emptyList()
            )
        }

        state.goal = content
        state.desiredOutcome = content
        state.depth = 4

        return ChatResponse(
            message = chatBotService.getProfileQuestion(state.subCategory ?: "선택한 분야"),
            nextDepth = 4,
            completed = false,
            options = emptyList()
        )
    }

    private fun handleProfile(content: String, state: ChatState): ChatResponse {
        val invalidSkill = chatValidationService.isClearlyInvalidSkillLevelAnswer(content)
        val invalidRecent = chatValidationService.isClearlyInvalidRecentExperienceAnswer(content)
        if (content.isBlank() || (invalidSkill && invalidRecent)) {
            return ChatResponse(
                message = chatBotService.getProfileQuestion(state.subCategory ?: "선택한 분야"),
                nextDepth = 4,
                completed = false,
                options = emptyList()
            )
        }

        state.skillLevel = content
        state.recentExperience = content
        state.depth = 5

        return ChatResponse(
            message = chatBotService.getScheduleQuestion(),
            nextDepth = 5,
            completed = false,
            options = emptyList()
        )
    }

    private fun handleSchedule(content: String, state: ChatState): ChatResponse {
        if (content.isBlank() || chatValidationService.isClearlyNonAnswer(content)) {
            return ChatResponse(
                message = chatBotService.getScheduleQuestion(),
                nextDepth = 5,
                completed = false,
                options = emptyList()
            )
        }

        applyScheduleAnswer(content, state)

        val readiness = chatValidationService.evaluateRecommendationReadiness(state)
        if (readiness.sufficient) {
            return finalizeWithRecommendation(state)
        }

        state.needFollowUp = true
        state.followUpQuestion = chatBotService.getSupplementQuestion(readiness.missingFields)
        state.depth = 6

        return ChatResponse(
            message = state.followUpQuestion!!,
            nextDepth = 6,
            completed = false,
            options = emptyList()
        )
    }

    private fun handleSupplement(content: String, state: ChatState): ChatResponse {
        if (content.isBlank() || chatValidationService.isClearlyNonAnswer(content)) {
            return ChatResponse(
                message = state.followUpQuestion ?: "부족한 정보를 조금 더 구체적으로 입력해 주세요.",
                nextDepth = 6,
                completed = false,
                options = emptyList()
            )
        }

        val before = chatValidationService.evaluateRecommendationReadiness(state)
        applySupplementAnswer(content, state, before.missingFields)
        state.followUpAnswer = content

        val after = chatValidationService.evaluateRecommendationReadiness(state)
        if (after.sufficient) {
            return finalizeWithRecommendation(state)
        }

        state.followUpQuestion = chatBotService.getSupplementQuestion(after.missingFields)
        state.depth = 6
        return ChatResponse(
            message = state.followUpQuestion!!,
            nextDepth = 6,
            completed = false,
            options = emptyList()
        )
    }

    private fun finalizeWithRecommendation(state: ChatState): ChatResponse {
        val missions = runBlocking {
            chatBotMissionRecommendationService.recommend(
                ChatBotMissionContext(
                    category = state.category.orEmpty(),
                    subCategory = state.subCategory.orEmpty(),
                    goal = state.goal.orEmpty(),
                    desiredOutcome = state.desiredOutcome ?: state.goal.orEmpty(),
                    skillLevel = state.skillLevel.orEmpty(),
                    recentExperience = state.recentExperience ?: state.skillLevel.orEmpty(),
                    targetPeriod = state.targetPeriod ?: state.dailyAvailableTime.orEmpty(),
                    dailyAvailableTime = state.dailyAvailableTime ?: state.targetPeriod.orEmpty(),
                    additionalOpinion = state.additionalOpinion ?: state.followUpAnswer
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

    private fun handleReset(state: ChatState): ChatResponse {
        chatBotService.resetState(state)
        return ChatResponse(
            message = chatBotService.getRestartMessage(),
            nextDepth = 1,
            completed = false,
            options = buildJobOptions()
        )
    }

    private fun applyScheduleAnswer(answer: String, state: ChatState) {
        val dailyTime = extractDailyTime(answer)
        val targetPeriod = extractTargetPeriod(answer)

        state.dailyAvailableTime = dailyTime ?: state.dailyAvailableTime ?: answer
        state.targetPeriod = targetPeriod ?: state.targetPeriod ?: answer
    }

    private fun applySupplementAnswer(answer: String, state: ChatState, missingFields: List<String>) {
        if ("goal" in missingFields) {
            state.goal = mergeAnswer(state.goal, answer)
            state.desiredOutcome = mergeAnswer(state.desiredOutcome, answer)
        }

        if ("experience" in missingFields) {
            state.skillLevel = mergeAnswer(state.skillLevel, answer)
            state.recentExperience = mergeAnswer(state.recentExperience, answer)
        }

        if ("schedule" in missingFields) {
            applyScheduleAnswer(answer, state)
        }
    }

    private fun mergeAnswer(current: String?, addition: String): String {
        if (current.isNullOrBlank()) return addition
        if (current.contains(addition)) return current
        return "$current / $addition"
    }

    private fun extractDailyTime(answer: String): String? {
        val matches = DAILY_TIME_REGEX.findAll(answer).map { it.value.trim() }.toList()
        return if (matches.isEmpty()) null else matches.joinToString(", ")
    }

    private fun extractTargetPeriod(answer: String): String? {
        val matches = TARGET_PERIOD_REGEX.findAll(answer).map { it.value.trim() }.toList()
        return if (matches.isEmpty()) null else matches.joinToString(", ")
    }

    private fun buildJobOptions(): List<ChatOption> {
        return jobService.getJobList().map { dto ->
            ChatOption(
                no = dto.id,
                label = dto.jobName,
                type = ChatOptionType.JOB
            )
        }
    }

    private fun buildJobDetailOptions(categoryNo: Long): List<ChatOption> {
        return jobDetailService.getJobDetailList(categoryNo).map { dto ->
            ChatOption(
                no = dto.id,
                label = dto.jobDetailName,
                type = ChatOptionType.JOB_DETAIL
            )
        }
    }

    companion object {
        private val RESET_COMMANDS = setOf("reset", "restart", "처음부터", "다시")
        private val DAILY_TIME_REGEX = Regex("(\\d+)\\s*(시간|분)")
        private val TARGET_PERIOD_REGEX = Regex("(\\d+)\\s*(일|주|개월|달|년)")
    }
}
