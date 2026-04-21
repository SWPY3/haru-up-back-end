package com.haruUp.chat.application

import com.haruUp.category.application.JobDetailService
import com.haruUp.category.application.JobService
import com.haruUp.category.domain.dto.JobDetailDto
import com.haruUp.category.domain.dto.JobDto
import com.haruUp.chat.application.service.ChatBotMissionContext
import com.haruUp.chat.application.service.ChatBotMissionRecommendationService
import com.haruUp.chat.application.service.ChatBotService
import com.haruUp.chat.application.service.ChatValidationService
import com.haruUp.chat.application.service.RecommendationReadiness
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
    fun `first entry starts from goal question`() {
        val sessionId = "session-intro"
        whenever(chatRedisRepository.findBySessionId(sessionId)).thenReturn(null)

        val response = chatBotUseCase.chatWithBot(ChatRequest(sessionId = sessionId, content = ""))

        assertEquals(1, response.nextDepth)
        assertFalse(response.completed)
        assertTrue(response.options.isEmpty())
        assertTrue(response.message.contains("1번 질문"))
        verify(chatRedisRepository).saveChatState(eq(sessionId), any())
    }

    @Test
    fun `fixed 4 questions complete recommendation`() {
        val sessionId = "session-recommend"
        val state = ChatState(depth = 1)
        whenever(chatRedisRepository.findBySessionId(sessionId)).thenReturn(state)

        whenever(chatValidationService.isClearlyNonAnswer(any())).thenReturn(false)
        whenever(chatValidationService.isClearlyInvalidSkillLevelAnswer(any())).thenReturn(false)
        whenever(chatValidationService.isClearlyInvalidTargetPeriod(any())).thenReturn(false)
        whenever(chatValidationService.isClearlyInvalidDailyTime(any())).thenReturn(false)
        whenever(chatValidationService.evaluateRecommendationReadiness(any())).thenReturn(
            RecommendationReadiness(sufficient = true, missingFields = emptyList())
        )

        whenever(jobService.getJobList()).thenReturn(
            listOf(JobDto(1L, "직장인"), JobDto(2L, "학생"))
        )
        whenever(jobDetailService.getJobDetailList(1L)).thenReturn(
            listOf(JobDetailDto(11L, 1L, "백엔드 개발"))
        )
        whenever(jobDetailService.getJobDetailList(2L)).thenReturn(
            listOf(JobDetailDto(21L, 2L, "학습 전략"))
        )

        runBlocking {
            whenever(chatBotMissionRecommendationService.recommend(any())).thenReturn(
                listOf(
                    MissionDto(content = "오늘의 미션 1", difficulty = 1),
                    MissionDto(content = "오늘의 미션 2", difficulty = 2)
                )
            )
        }

        val q1 = chatBotUseCase.chatWithBot(
            ChatRequest(sessionId = sessionId, content = "2개월 내에 백엔드 API 서버를 구축하고 싶어요.")
        )
        assertEquals(2, q1.nextDepth)
        assertFalse(q1.completed)

        val q2 = chatBotUseCase.chatWithBot(
            ChatRequest(sessionId = sessionId, content = "스프링 기초 수준이고 토이 프로젝트 경험이 있어요.")
        )
        assertEquals(3, q2.nextDepth)
        assertFalse(q2.completed)

        val q3 = chatBotUseCase.chatWithBot(
            ChatRequest(sessionId = sessionId, content = "목표 기간은 2개월입니다.")
        )
        assertEquals(4, q3.nextDepth)
        assertFalse(q3.completed)

        val q4 = chatBotUseCase.chatWithBot(
            ChatRequest(sessionId = sessionId, content = "하루 1시간 가능합니다.")
        )
        assertEquals(999, q4.nextDepth)
        assertTrue(q4.completed)
        assertTrue(q4.message.contains("미션"))

        verify(chatRedisRepository, times(3)).saveChatState(eq(sessionId), any())
        verify(chatRedisRepository).deleteBySessionId(sessionId)

        val contextCaptor = argumentCaptor<ChatBotMissionContext>()
        runBlocking {
            verify(chatBotMissionRecommendationService).recommend(contextCaptor.capture())
        }
        assertEquals("직장인", contextCaptor.firstValue.category)
        assertEquals("백엔드 개발", contextCaptor.firstValue.subCategory)
        assertEquals("목표 기간은 2개월입니다.", contextCaptor.firstValue.targetPeriod)
        assertEquals("하루 1시간 가능합니다.", contextCaptor.firstValue.dailyAvailableTime)
    }

    @Test
    fun `if period answer is invalid it asks period question again`() {
        val sessionId = "session-period"
        val state = ChatState(depth = 3)
        whenever(chatRedisRepository.findBySessionId(sessionId)).thenReturn(state)
        whenever(chatValidationService.isClearlyInvalidTargetPeriod(any())).thenReturn(true)

        val response = chatBotUseCase.chatWithBot(ChatRequest(sessionId = sessionId, content = "몰라요"))

        assertEquals(3, response.nextDepth)
        assertFalse(response.completed)
        assertTrue(response.message.contains("3번 질문"))
    }

    @Test
    fun `reset command restarts conversation`() {
        val sessionId = "session-reset"
        val state = ChatState(depth = 2, goal = "기존 목표", skillLevel = "기존 실력")
        whenever(chatRedisRepository.findBySessionId(sessionId)).thenReturn(state)

        val response = chatBotUseCase.chatWithBot(ChatRequest(sessionId = sessionId, content = "reset"))

        assertEquals(1, response.nextDepth)
        assertFalse(response.completed)
        assertTrue(response.options.isEmpty())
        assertTrue(state.goal.isNullOrBlank())
        verify(chatRedisRepository).saveChatState(eq(sessionId), any())
        verify(chatRedisRepository, never()).deleteBySessionId(sessionId)
    }
}
