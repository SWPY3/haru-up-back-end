package com.haruUp.chat.application

import com.haruUp.category.application.JobDetailService
import com.haruUp.category.application.JobService
import com.haruUp.chat.application.service.ChatBotService
import com.haruUp.chat.application.service.ChatValidationService
import com.haruUp.chat.domain.ChatOption
import com.haruUp.chat.domain.ChatOptionType
import com.haruUp.chat.domain.ChatRequest
import com.haruUp.chat.domain.ChatResponse
import com.haruUp.chat.domain.ChatState
import com.haruUp.chat.repository.ChatRedisRepository
import org.springframework.stereotype.Component

@Component
class ChatBotUseCase(
    private final var jobService : JobService,
    private final var jobDetailService : JobDetailService,
    private final var chatBotService: ChatBotService,
    private final var chatValidationService : ChatValidationService,
    private final var chatRedisRepository: ChatRedisRepository,
) {

    /**
     * 챗봇 메인 진입점
     *
     * 전체 흐름:
     * 1. Redis에서 현재 sessionId의 ChatState 조회
     * 2. 없으면 새 ChatState 생성
     * 3. depth에 따라 단계별 처리
     * 4. 응답이 완료면 상태 삭제, 아니면 상태 저장
     * 5. 최종 응답 반환
     */
    fun chatWithBot(request: ChatRequest): ChatResponse {
        // 현재 세션의 챗봇 상태 조회
        // 상태가 없으면 첫 진입으로 판단하고 새로운 상태 생성
        val state = chatRedisRepository.findBySessionId(request.sessionId) ?: ChatState()

        // 현재 depth에 따라 처리 단계 분기
        val response = when (state.depth) {
            0 -> handleIntro(state)
            1 -> handleCategory(request.content, state)
            2 -> handleSubCategory(request.content, state)
            3 -> handleGoal(request.content, state)
            4 -> handleDesiredOutcome(request.content, state)
            5 -> handleSkillLevel(request.content, state)
            6 -> handleRecentExperience(request.content, state)
            7 -> handleTargetPeriod(request.content, state)
            8 -> handleDailyAvailableTime(request.content, state)
            9 -> handleAdditionalOpinion(request.content, state)
            else -> handleReset(state)
        }

        // 대화가 완료되면 Redis 상태 삭제
        // 아직 진행 중이면 최신 상태를 다시 저장
        if (response.completed) {
            chatRedisRepository.deleteBySessionId(request.sessionId)
        } else {
            chatRedisRepository.saveChatState(request.sessionId, state)
        }

        return response
    }

    /**
     * 0단계: 첫 진입 처리
     *
     * 사용자가 챗봇에 처음 들어왔을 때 소개 문구와
     * 상위 관심사 목록을 반환한다.
     */
    private fun handleIntro(state: ChatState): ChatResponse {
        // 다음 단계는 상위 관심사 선택 단계
        state.depth = 1

        return ChatResponse(
            message = chatBotService.getIntroMessage(),
            nextDepth = 1,
            completed = false,
            options = jobService.getJobList().map { dto ->
                ChatOption(
                    no =  dto.id,
                    label =  dto.jobName,
                    type = ChatOptionType.JOB
                )
             }
        )
    }


    /**
     * 1단계: 상위 관심사 선택 처리
     *
     * 예:
     * - 외국어 공부
     * - 재테크 및 투자
     * - 체력관리 및 운동
     */
    private fun handleCategory(content: String, state: ChatState): ChatResponse {
        val answer = content.trim()
        val categoryOptions = jobService.getJobList()

        if (answer.isBlank()) {
            return ChatResponse(
                message = "관심 분야를 선택해주세요.",
                nextDepth = 1,
                completed = false,
                options = categoryOptions.map { dto ->
                    ChatOption(
                        no = dto.id,
                        label = dto.jobName,
                        type = ChatOptionType.JOB
                    )
                }
            )
        }

        val answerNo = answer.toLongOrNull()
        if (answerNo == null) {
            return ChatResponse(
                message = "아래 항목 중에서 선택해주세요.",
                nextDepth = 1,
                completed = false,
                options = categoryOptions.map { dto ->
                    ChatOption(
                        no = dto.id,
                        label = dto.jobName,
                        type = ChatOptionType.JOB
                    )
                }
            )
        }

        val selectedCategory = categoryOptions.find { it.id == answerNo }
            ?: return ChatResponse(
                message = "아래 항목 중에서 선택해주세요.",
                nextDepth = 1,
                completed = false,
                options = categoryOptions.map { dto ->
                    ChatOption(
                        no = dto.id,
                        label = dto.jobName,
                        type = ChatOptionType.JOB
                    )
                }
            )

        state.categoryNo = selectedCategory.id
        state.category = selectedCategory.jobName
        state.depth = 2

        return ChatResponse(
            message = chatBotService.getSubCategoryQuestion(selectedCategory.jobName),
            nextDepth = 2,
            completed = false,
            options = jobDetailService.getJobDetailList(selectedCategory.id).map { dto ->
                ChatOption(
                    no = dto.id,
                    label = dto.jobDetailName,
                    type = ChatOptionType.JOB_DETAIL
                )
            }
        )
    }

    /**
     * 2단계: 세부 관심사 선택 처리
     *
     * 예:
     * category = "외국어 공부"
     * answer = "영어"
     */
    private fun handleSubCategory(content: String, state: ChatState): ChatResponse {
        val answer = content.trim()

        if (state.categoryNo == null || state.category.isNullOrBlank()) {
            chatBotService.resetState(state)

            return ChatResponse(
                message = chatBotService.getRestartMessage(),
                nextDepth = 1,
                completed = false,
                options = jobService.getJobList().map { dto ->
                    ChatOption(
                        no = dto.id,
                        label = dto.jobName,
                        type = ChatOptionType.JOB
                    )
                }
            )
        }

        val subCategoryOptions = jobDetailService.getJobDetailList(state.categoryNo!!)

        if (answer.isBlank()) {
            return ChatResponse(
                message = "세부 관심사나 직무를 선택해주세요.",
                nextDepth = 2,
                completed = false,
                options = subCategoryOptions.map { dto ->
                    ChatOption(
                        no = dto.id,
                        label = dto.jobDetailName,
                        type = ChatOptionType.JOB_DETAIL
                    )
                }
            )
        }

        val answerNo = answer.toLongOrNull()
        if (answerNo == null) {
            return ChatResponse(
                message = "아래 항목 중에서 선택해주세요.",
                nextDepth = 2,
                completed = false,
                options = subCategoryOptions.map { dto ->
                    ChatOption(
                        no = dto.id,
                        label = dto.jobDetailName,
                        type = ChatOptionType.JOB_DETAIL
                    )
                }
            )
        }

        val selectedSubCategory = subCategoryOptions.find { it.id == answerNo }
            ?: return ChatResponse(
                message = "아래 항목 중에서 선택해주세요.",
                nextDepth = 2,
                completed = false,
                options = subCategoryOptions.map { dto ->
                    ChatOption(
                        no = dto.id,
                        label = dto.jobDetailName,
                        type = ChatOptionType.JOB_DETAIL
                    )
                }
            )

        state.subCategoryNo = selectedSubCategory.id
        state.subCategory = selectedSubCategory.jobDetailName
        state.depth = 3

        return ChatResponse(
            message = chatBotService.getGoalQuestion(
                category = state.category!!,
                subCategory = state.subCategory!!
            ),
            nextDepth = 3,
            completed = false,
            options = emptyList()
        )
    }

    /**
     * 3단계: 현재 목표 입력 처리
     */
    private fun handleGoal(content: String, state: ChatState): ChatResponse {
        val answer = content.trim()
        val question = chatBotService.getGoalQuestion(
            category = state.category ?: "선택한 분야",
            subCategory = state.subCategory ?: "선택한 분야"
        )

        if (answer.isBlank() || chatValidationService.isClearlyNonAnswer(answer)) {
            return ChatResponse(
                message = question,
                nextDepth = 3,
                completed = false,
                options = emptyList()
            )
        }

        state.goal = answer
        state.depth = 4

        return ChatResponse(
            message = chatBotService.getDesiredOutcomeQuestion(state.subCategory ?: "선택한 분야"),
            nextDepth = 4,
            completed = false,
            options = emptyList()
        )
    }

    /**
     * 4단계: 최종 결과물 입력 처리
     */
    private fun handleDesiredOutcome(content: String, state: ChatState): ChatResponse {
        val answer = content.trim()
        val question = chatBotService.getDesiredOutcomeQuestion(state.subCategory ?: "선택한 분야")

        if (answer.isBlank() || chatValidationService.isClearlyNonAnswer(answer)) {
            return ChatResponse(
                message = question,
                nextDepth = 4,
                completed = false,
                options = emptyList()
            )
        }

        state.desiredOutcome = answer
        state.depth = 5

        return ChatResponse(
            message = chatBotService.getSkillLevelQuestion(state.subCategory ?: "선택한 분야"),
            nextDepth = 5,
            completed = false,
            options = emptyList()
        )
    }

    /**
     * 5단계: 현재 실력 입력 처리
     */
    private fun handleSkillLevel(content: String, state: ChatState): ChatResponse {
        val answer = content.trim()
        val question = chatBotService.getSkillLevelQuestion(state.subCategory ?: "선택한 분야")

        if (chatValidationService.isClearlyInvalidSkillLevelAnswer(answer)) {
            return ChatResponse(
                message = question,
                nextDepth = 5,
                completed = false,
                options = emptyList()
            )
        }

        state.skillLevel = answer
        state.depth = 6

        return ChatResponse(
            message = chatBotService.getRecentExperienceQuestion(state.subCategory ?: "선택한 분야"),
            nextDepth = 6,
            completed = false,
            options = emptyList()
        )
    }

    /**
     * 6단계: 최근 직접 해본 작업 입력 처리
     */
    private fun handleRecentExperience(content: String, state: ChatState): ChatResponse {
        val answer = content.trim()
        val question = chatBotService.getRecentExperienceQuestion(state.subCategory ?: "선택한 분야")

        if (chatValidationService.isClearlyInvalidRecentExperienceAnswer(answer)) {
            return ChatResponse(
                message = question,
                nextDepth = 6,
                completed = false,
                options = emptyList()
            )
        }

        state.recentExperience = answer
        state.depth = 7

        return ChatResponse(
            message = chatBotService.getTargetPeriodQuestion(),
            nextDepth = 7,
            completed = false,
            options = emptyList()
        )
    }

    /**
     * 7단계: 목표 기간 입력 처리
     */
    private fun handleTargetPeriod(content: String, state: ChatState): ChatResponse {
        val answer = content.trim()
        val question = chatBotService.getTargetPeriodQuestion()

        if (answer.isBlank() || chatValidationService.isClearlyNonAnswer(answer)) {
            return ChatResponse(
                message = question,
                nextDepth = 7,
                completed = false,
                options = emptyList()
            )
        }

        state.targetPeriod = answer
        state.depth = 8

        return ChatResponse(
            message = chatBotService.getDailyAvailableTimeQuestion(),
            nextDepth = 8,
            completed = false,
            options = emptyList()
        )
    }

    /**
     * 8단계: 하루 투자 가능 시간 입력 처리
     */
    private fun handleDailyAvailableTime(content: String, state: ChatState): ChatResponse {
        val answer = content.trim()
        val question = chatBotService.getDailyAvailableTimeQuestion()

        if (answer.isBlank() || chatValidationService.isClearlyNonAnswer(answer)) {
            return ChatResponse(
                message = question,
                nextDepth = 8,
                completed = false,
                options = emptyList()
            )
        }

        state.dailyAvailableTime = answer
        state.depth = 9

        return ChatResponse(
            message = chatBotService.getAdditionalOpinionQuestion(),
            nextDepth = 9,
            completed = false,
            options = emptyList()
        )
    }

    /**
     * 9단계: 추가 의견 입력 처리
     */
    private fun handleAdditionalOpinion(content: String, state: ChatState): ChatResponse {
        val answer = content.trim()

        state.additionalOpinion = if (isNoAdditionalOpinion(answer)) {
            null
        } else {
            answer
        }
        state.depth = 999

        return ChatResponse(
            message = chatBotService.buildFinalMessage(state),
            nextDepth = 999,
            completed = true,
            options = emptyList()
        )
    }

    private fun isNoAdditionalOpinion(answer: String): Boolean {
        if (answer.isBlank()) {
            return true
        }

        val normalized = answer.trim()
            .lowercase()
            .replace("\\s+".toRegex(), " ")
            .replace("[?!.,~]+$".toRegex(), "")

        return normalized in setOf(
            "없음",
            "없어",
            "없어요",
            "없습니다",
            "딱히 없어",
            "딱히 없음",
            "딱히 없어요",
            "없는데"
        )
    }

    /**
     * 비정상 상태 복구
     *
     * depth 값이 예상 범위를 벗어나거나,
     * 상태가 꼬였을 경우 처음부터 다시 시작하도록 초기화한다.
     */
    private fun handleReset(state: ChatState): ChatResponse {
        chatBotService.resetState(state)

        return ChatResponse(
            message = chatBotService.getRestartMessage(),
            nextDepth = 1,
            completed = false,
            options = jobService.getJobList().map { dto ->
                ChatOption(
                    no = dto.id,
                    label = dto.jobName,
                    type = ChatOptionType.JOB
                )
            }
        )
    }
}
