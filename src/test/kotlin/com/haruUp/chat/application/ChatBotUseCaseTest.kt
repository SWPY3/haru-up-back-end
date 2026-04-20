package com.haruUp.chat.application

import com.haruUp.category.application.JobDetailService
import com.haruUp.category.application.JobService
import com.haruUp.category.domain.dto.JobDetailDto
import com.haruUp.category.domain.dto.JobDto
import com.haruUp.chat.application.service.ChatBotMissionContext
import com.haruUp.chat.application.service.ChatBotMissionRecommendationService
import com.haruUp.chat.application.service.ChatBotService
import com.haruUp.chat.application.service.ChatValidationService
import com.haruUp.chat.domain.ChatOptionType
import com.haruUp.chat.domain.ChatRequest
import com.haruUp.chat.domain.ChatState
import com.haruUp.chat.repository.ChatRedisRepository
import com.haruUp.missionembedding.dto.MissionDto
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExtendWith(MockitoExtension::class)
class ChatBotUseCaseTest {

    private val chatBotService = ChatBotService()

    @Mock
    lateinit var jobService: JobService

    @Mock
    lateinit var jobDetailService: JobDetailService

    @Mock
    lateinit var chatValidationService: ChatValidationService

    @Mock
    lateinit var chatRedisRepository: ChatRedisRepository

    @Mock
    lateinit var chatBotMissionRecommendationService: ChatBotMissionRecommendationService

    lateinit var chatBotUseCase: ChatBotUseCase

    @BeforeEach
    fun setUp() {
        chatBotUseCase = ChatBotUseCase(
            jobService = jobService,
            jobDetailService = jobDetailService,
            chatBotService = chatBotService,
            chatValidationService = chatValidationService,
            chatRedisRepository = chatRedisRepository,
            chatBotMissionRecommendationService = chatBotMissionRecommendationService
        )
    }

    @Test
    fun `첫 진입 시 관심분야 선택지를 반환한다`() {
        val sessionId = "session-intro"
        whenever(chatRedisRepository.findBySessionId(sessionId)).thenReturn(null)
        whenever(jobService.getJobList()).thenReturn(
            listOf(
                JobDto(1L, "직장인"),
                JobDto(2L, "학생")
            )
        )

        val response = chatBotUseCase.chatWithBot(ChatRequest(sessionId = sessionId, content = ""))

        assertEquals(1, response.nextDepth)
        assertFalse(response.completed)
        assertEquals(2, response.options.size)
        assertTrue(response.options.all { it.type == ChatOptionType.JOB })
        verify(chatRedisRepository).saveChatState(eq(sessionId), any())
    }

    @Test
    fun `고정 3문항이 충분하면 미션 추천으로 종료한다`() = runBlocking {
        val sessionId = "session-recommend"
        val state = ChatState(
            depth = 3,
            categoryNo = 1L,
            category = "직장인",
            subCategoryNo = 11L,
            subCategory = "백엔드 개발"
        )
        whenever(chatRedisRepository.findBySessionId(sessionId)).thenReturn(state)
        whenever(chatValidationService.isClearlyNonAnswer(any())).thenReturn(false)
        whenever(chatValidationService.isClearlyInvalidSkillLevelAnswer(any())).thenReturn(false)
        whenever(chatValidationService.isClearlyInvalidRecentExperienceAnswer(any())).thenReturn(false)
        whenever(chatValidationService.evaluateRecommendationReadiness(any()))
            .thenReturn(com.haruUp.chat.application.service.RecommendationReadiness(true, emptyList()))
        whenever(chatBotMissionRecommendationService.recommend(any())).thenReturn(
            listOf(
                MissionDto(content = "오늘은 API 에러 핸들링 로직 개선하기", difficulty = 2),
                MissionDto(content = "테스트 코드 3개 추가하기", difficulty = 3)
            )
        )

        val goalResponse = chatBotUseCase.chatWithBot(
            ChatRequest(sessionId = sessionId, content = "2개월 안에 실서비스 수준 API를 만들고 싶어요.")
        )
        assertEquals(4, goalResponse.nextDepth)
        assertFalse(goalResponse.completed)

        val profileResponse = chatBotUseCase.chatWithBot(
            ChatRequest(sessionId = sessionId, content = "스프링은 기초 수준이고 개인 프로젝트 1개를 해봤어요.")
        )
        assertEquals(5, profileResponse.nextDepth)
        assertFalse(profileResponse.completed)

        val scheduleResponse = chatBotUseCase.chatWithBot(
            ChatRequest(sessionId = sessionId, content = "하루 1시간, 목표 기간은 2개월입니다.")
        )
        assertTrue(scheduleResponse.completed)
        assertEquals(999, scheduleResponse.nextDepth)
        assertTrue(scheduleResponse.message.contains("미션을 생성했어요"))
        assertTrue(scheduleResponse.message.contains("API 에러 핸들링"))

        verify(chatRedisRepository, times(2)).saveChatState(eq(sessionId), any())
        verify(chatRedisRepository).deleteBySessionId(sessionId)

        val contextCaptor = argumentCaptor<ChatBotMissionContext>()
        verify(chatBotMissionRecommendationService).recommend(contextCaptor.capture())
        assertEquals("직장인", contextCaptor.firstValue.category)
        assertEquals("백엔드 개발", contextCaptor.firstValue.subCategory)
    }

    @Test
    fun `고정 질문 이후 데이터가 부족하면 보완 질문을 한다`() = runBlocking {
        val sessionId = "session-supplement"
        val state = ChatState(
            depth = 5,
            categoryNo = 1L,
            category = "직장인",
            subCategoryNo = 11L,
            subCategory = "백엔드 개발",
            goal = "포트폴리오용 백엔드 결과물을 만들고 싶어요.",
            desiredOutcome = "포트폴리오용 백엔드 결과물을 만들고 싶어요.",
            skillLevel = "기초 수준입니다.",
            recentExperience = "간단한 CRUD API 구현 경험이 있어요."
        )
        whenever(chatRedisRepository.findBySessionId(sessionId)).thenReturn(state)
        whenever(chatValidationService.isClearlyNonAnswer(any())).thenReturn(false)
        whenever(chatValidationService.evaluateRecommendationReadiness(any()))
            .thenReturn(
                com.haruUp.chat.application.service.RecommendationReadiness(false, listOf("schedule")),
                com.haruUp.chat.application.service.RecommendationReadiness(true, emptyList())
            )
        whenever(chatBotMissionRecommendationService.recommend(any())).thenReturn(
            listOf(MissionDto(content = "하루 1시간 기준 미션 1", difficulty = 1))
        )

        val first = chatBotUseCase.chatWithBot(
            ChatRequest(sessionId = sessionId, content = "하루 1시간 가능해요.")
        )
        assertFalse(first.completed)
        assertEquals(6, first.nextDepth)
        assertTrue(first.message.contains("보완"))

        val second = chatBotUseCase.chatWithBot(
            ChatRequest(sessionId = sessionId, content = "2개월 안에 완료하고 싶어요.")
        )
        assertTrue(second.completed)
        assertEquals(999, second.nextDepth)
        assertTrue(second.message.contains("미션"))
    }

    @Test
    fun `reset 입력 시 초기화하고 다시 시작한다`() {
        val sessionId = "session-reset"
        val state = ChatState(
            depth = 4,
            categoryNo = 1L,
            category = "직장인",
            subCategoryNo = 11L,
            subCategory = "백엔드 개발",
            goal = "기존 목표"
        )
        whenever(chatRedisRepository.findBySessionId(sessionId)).thenReturn(state)
        whenever(jobService.getJobList()).thenReturn(
            listOf(JobDto(1L, "직장인"))
        )

        val response = chatBotUseCase.chatWithBot(
            ChatRequest(sessionId = sessionId, content = "reset")
        )

        assertEquals(1, response.nextDepth)
        assertFalse(response.completed)
        assertTrue(response.options.all { it.type == ChatOptionType.JOB })
        assertEquals(1, state.depth)
        assertTrue(state.category.isNullOrBlank())
        verify(chatRedisRepository).saveChatState(eq(sessionId), any())
        verify(chatRedisRepository, never()).deleteBySessionId(sessionId)
    }

    @Test
    fun `상세 관심사 선택 후 고정 질문 1번으로 이동한다`() {
        val sessionId = "session-sub-category"
        val state = ChatState(
            depth = 2,
            categoryNo = 1L,
            category = "직장인"
        )
        whenever(chatRedisRepository.findBySessionId(sessionId)).thenReturn(state)
        whenever(jobDetailService.getJobDetailList(1L)).thenReturn(
            listOf(
                JobDetailDto(11L, 1L, "백엔드 개발"),
                JobDetailDto(12L, 1L, "기획")
            )
        )

        val response = chatBotUseCase.chatWithBot(
            ChatRequest(sessionId = sessionId, content = "11")
        )

        assertEquals(3, response.nextDepth)
        assertFalse(response.completed)
        assertEquals("백엔드 개발", state.subCategory)
        assertTrue(response.message.contains("목표/결과물"))
    }
}
