package com.haruUp.chat.application

import com.haruUp.category.application.JobDetailService
import com.haruUp.category.application.JobService
import com.haruUp.chat.application.service.ChatBotService
import com.haruUp.chat.application.service.ChatValidationService
import com.haruUp.chat.domain.ChatDto
import com.haruUp.chat.domain.ChatOption
import com.haruUp.chat.domain.ChatOptionType
import com.haruUp.chat.domain.ChatRequest
import com.haruUp.chat.domain.ChatResponse
import com.haruUp.chat.domain.ChatState
import com.haruUp.chat.domain.ValidationResult
import com.haruUp.chat.repository.ChatRedisRepository
import com.haruUp.global.clova.ClovaApiClient
import com.haruUp.interest.service.MemberInterestService
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
            3 -> handleMotivation(request.content, state)
            4 -> handleFollowUp(request.content, state)
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
                message = "자기계발 분야를 선택해주세요.",
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
                message = "처음부터 다시 시작할게요. 어떤 자기계발을 하고 싶으신가요?",
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
                message = "세부 관심사를 선택해주세요.",
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
            message = chatBotService.getMotivationQuestion(
                category = state.category!!,
                subCategory = state.subCategory!!
            ),
            nextDepth = 3,
            completed = false,
            options = emptyList()
        )
    }

    /**
     * 3단계: 학습 동기 입력 처리
     *
     * 사용자의 동기 답변을 상태에 저장한 뒤,
     * LLM API를 사용하여 충분한 답변인지 검수한다.
     *
     * 검수 결과:
     * - 충분함 -> 종료
     * - 부족함 -> 추가 질문 단계로 이동
     */
    private fun handleMotivation(content: String, state: ChatState): ChatResponse {
        val answer = content.trim()

        // 공백 입력 방지
        if (answer.isBlank()) {
            return ChatResponse(
                message = "학습 동기를 입력해주세요.",
                nextDepth = 3,
                completed = false,
                options = emptyList()
            )
        }

        // 사용자의 학습 동기 저장
        state.motivation = answer

        // LLM을 통한 답변 검수
        val validation: ValidationResult = chatValidationService.validateMotivation(state)

        return if (validation.isValid) {
            // 현재 답변만으로 충분한 경우 종료
            state.depth = 999

            ChatResponse(
                message = chatBotService.buildFinalMessage(state),
                nextDepth = 999,
                completed = true,
                options = emptyList()
            )
        } else {
            // 답변이 부족하면 추가 질문 단계로 이동
            state.needFollowUp = true
            state.followUpQuestion = validation.followUpQuestion
                ?: "조금 더 구체적으로 설명해주실 수 있을까요?"
            state.depth = 4

            ChatResponse(
                message = state.followUpQuestion!!,
                nextDepth = 4,
                completed = false,
                options = emptyList()
            )
        }
    }

    /**
     * 4단계: 추가 질문 답변 처리
     *
     * 3단계에서 답변이 부족하다고 판단된 경우,
     * 사용자에게 추가 질문을 던지고 그 답변을 저장한 후 종료한다.
     */
    private fun handleFollowUp(content: String, state: ChatState): ChatResponse {
        val answer = content.trim()

        // 공백 입력 방지
        if (answer.isBlank()) {
            return ChatResponse(
                message = state.followUpQuestion ?: "조금 더 구체적으로 입력해주세요.",
                nextDepth = 4,
                completed = false,
                options = emptyList()
            )
        }

        // 추가 질문에 대한 답변 저장
        state.followUpAnswer = answer

        // 최종 종료 상태로 전환
        state.depth = 999

        return ChatResponse(
            message = chatBotService.buildFinalMessage(state),
            nextDepth = 999,
            completed = true,
            options = emptyList()
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
            message = "처음부터 다시 시작할게요. 어떤 자기계발을 하고 싶으신가요?",
            nextDepth = 1,
            completed = false,
            options = jobService.getJobList().map { dto ->
                ChatOption(
                    no = dto.id,
                    label = dto.jobName,
                    type = ChatOptionType.JOB_DETAIL
                )
            }
        )
    }
}