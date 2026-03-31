package com.haruUp.chat.application

import com.haruUp.category.application.JobDetailService
import com.haruUp.category.application.JobService
import com.haruUp.category.domain.dto.JobDetailDto
import com.haruUp.category.domain.dto.JobDto
import com.haruUp.chat.application.service.ChatBotService
import com.haruUp.chat.application.service.ChatValidationService
import com.haruUp.chat.domain.ChatOptionType
import com.haruUp.chat.domain.ChatRequest
import com.haruUp.chat.domain.ChatState
import com.haruUp.chat.repository.ChatRedisRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
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

    lateinit var chatBotUseCase: ChatBotUseCase

    @BeforeEach
    fun setUp() {
        chatBotUseCase = ChatBotUseCase(
            jobService = jobService,
            jobDetailService = jobDetailService,
            chatBotService = chatBotService,
            chatValidationService = chatValidationService,
            chatRedisRepository = chatRedisRepository
        )
    }

    @Test
    fun `첫 진입 시 소개 메시지와 관심사 목록을 반환하고 상태를 저장한다`() {
        val sessionId = "session-1"
        val jobs = listOf(
            JobDto(1L, "직장인"),
            JobDto(2L, "학생")
        )

        whenever(chatRedisRepository.findBySessionId(sessionId)).thenReturn(null)
        whenever(jobService.getJobList()).thenReturn(jobs)

        val response = chatBotUseCase.chatWithBot(ChatRequest(sessionId = sessionId, content = ""))

        assertEquals(chatBotService.getIntroMessage(), response.message)
        assertEquals(1, response.nextDepth)
        assertFalse(response.completed)
        assertEquals(2, response.options.size)
        assertTrue(response.options.all { it.type == ChatOptionType.JOB })

        val stateCaptor = argumentCaptor<ChatState>()
        verify(chatRedisRepository).saveChatState(eq(sessionId), stateCaptor.capture())
        verify(chatRedisRepository, never()).deleteBySessionId(any())
        assertEquals(1, stateCaptor.firstValue.depth)
    }

    @Test
    fun `비정상 depth 는 상태를 초기화하고 JOB 옵션으로 다시 시작한다`() {
        val sessionId = "session-reset"
        val corruptedState = ChatState(
            depth = 99,
            categoryNo = 1L,
            category = "직장인",
            subCategoryNo = 2L,
            subCategory = "개발자",
            goal = "앱을 만들고 싶어요",
            desiredOutcome = "와이어프레임 3장을 만들고 싶어요",
            skillLevel = "입문",
            recentExperience = "화면 1개 정도만 그려봤어요",
            targetPeriod = "3개월",
            dailyAvailableTime = "1시간",
            additionalOpinion = "실습 위주로 하고 싶어요"
        )
        val jobs = listOf(
            JobDto(1L, "직장인"),
            JobDto(2L, "학생")
        )

        whenever(chatRedisRepository.findBySessionId(sessionId)).thenReturn(corruptedState)
        whenever(jobService.getJobList()).thenReturn(jobs)

        val response = chatBotUseCase.chatWithBot(ChatRequest(sessionId = sessionId, content = "ignored"))

        assertEquals(chatBotService.getRestartMessage(), response.message)
        assertEquals(1, response.nextDepth)
        assertFalse(response.completed)
        assertTrue(response.options.all { it.type == ChatOptionType.JOB })

        assertEquals(1, corruptedState.depth)
        assertNull(corruptedState.category)
        assertNull(corruptedState.subCategory)
        assertNull(corruptedState.goal)
        assertNull(corruptedState.desiredOutcome)
        assertNull(corruptedState.skillLevel)
        assertNull(corruptedState.recentExperience)
        assertNull(corruptedState.targetPeriod)
        assertNull(corruptedState.dailyAvailableTime)
        assertNull(corruptedState.additionalOpinion)
    }

    @Test
    fun `세부 관심사 선택 후 목표 질문으로 이동한다`() {
        val sessionId = "session-subcategory"
        val state = ChatState(
            depth = 2,
            categoryNo = 1L,
            category = "직장인"
        )
        val details = listOf(
            JobDetailDto(2L, 1L, "개발자"),
            JobDetailDto(3L, 1L, "기획자")
        )

        whenever(chatRedisRepository.findBySessionId(sessionId)).thenReturn(state)
        whenever(jobDetailService.getJobDetailList(1L)).thenReturn(details)

        val response = chatBotUseCase.chatWithBot(ChatRequest(sessionId = sessionId, content = "2"))

        assertEquals(3, response.nextDepth)
        assertFalse(response.completed)
        assertTrue(response.message.contains("이루고 싶은 목표"))
        assertEquals("개발자", state.subCategory)
        assertEquals(3, state.depth)
    }

    @Test
    fun `모호한 목표 답변은 3단계에서 다시 입력받는다`() {
        val sessionId = "session-goal-vague"
        val state = ChatState(
            depth = 3,
            category = "직장인",
            subCategory = "개발자"
        )

        whenever(chatRedisRepository.findBySessionId(sessionId)).thenReturn(state)
        whenever(chatValidationService.isClearlyNonAnswer("잘 모르겠는데?")).thenReturn(true)

        val response = chatBotUseCase.chatWithBot(
            ChatRequest(sessionId = sessionId, content = "잘 모르겠는데?")
        )

        assertEquals(3, response.nextDepth)
        assertFalse(response.completed)
        assertNull(state.goal)
    }

    @Test
    fun `목표를 입력하면 최종 결과물 질문으로 이동한다`() {
        val sessionId = "session-goal"
        val state = ChatState(
            depth = 3,
            category = "직장인",
            subCategory = "개발자"
        )

        whenever(chatRedisRepository.findBySessionId(sessionId)).thenReturn(state)
        whenever(chatValidationService.isClearlyNonAnswer(any())).thenReturn(false)

        val response = chatBotUseCase.chatWithBot(
            ChatRequest(sessionId = sessionId, content = "3개월 안에 스프링으로 API 서버를 혼자 만들고 싶어요")
        )

        assertEquals(4, response.nextDepth)
        assertFalse(response.completed)
        assertEquals("3개월 안에 스프링으로 API 서버를 혼자 만들고 싶어요", state.goal)
        assertEquals(4, state.depth)
        assertTrue(response.message.contains("최종적으로 만들고 싶은 결과물"))
    }

    @Test
    fun `최종 결과물을 입력하면 현재 실력 질문으로 이동한다`() {
        val sessionId = "session-outcome"
        val state = ChatState(
            depth = 4,
            category = "직장인",
            subCategory = "개발자",
            goal = "스프링으로 API 서버를 만들고 싶어요"
        )

        whenever(chatRedisRepository.findBySessionId(sessionId)).thenReturn(state)
        whenever(chatValidationService.isClearlyNonAnswer(any())).thenReturn(false)

        val response = chatBotUseCase.chatWithBot(
            ChatRequest(sessionId = sessionId, content = "혼자서 실행되는 CRUD API 서버 1개를 완성하고 싶어요")
        )

        assertEquals(5, response.nextDepth)
        assertFalse(response.completed)
        assertEquals("혼자서 실행되는 CRUD API 서버 1개를 완성하고 싶어요", state.desiredOutcome)
        assertTrue(response.message.contains("현재 실력"))
    }

    @Test
    fun `현재 실력을 입력하면 최근 직접 해본 작업 질문으로 이동한다`() {
        val sessionId = "session-skill"
        val state = ChatState(
            depth = 5,
            category = "직장인",
            subCategory = "개발자",
            goal = "스프링으로 API 서버를 만들고 싶어요",
            desiredOutcome = "혼자서 실행되는 CRUD API 서버 1개를 완성하고 싶어요"
        )

        whenever(chatRedisRepository.findBySessionId(sessionId)).thenReturn(state)
        whenever(chatValidationService.isClearlyInvalidSkillLevelAnswer(any())).thenReturn(false)

        val response = chatBotUseCase.chatWithBot(
            ChatRequest(sessionId = sessionId, content = "자바 문법만 조금 알고 있는 입문 수준이에요")
        )

        assertEquals(6, response.nextDepth)
        assertFalse(response.completed)
        assertEquals("자바 문법만 조금 알고 있는 입문 수준이에요", state.skillLevel)
        assertTrue(response.message.contains("최근 직접 해본"))
    }

    @Test
    fun `해본 적 없는 형태의 현재 실력 답변도 정상 통과한다`() {
        val sessionId = "session-skill-beginner"
        val state = ChatState(
            depth = 5,
            category = "직장인",
            subCategory = "기획자",
            goal = "서비스 기획자가 되고 싶어요",
            desiredOutcome = "기획서 1장을 완성하고 싶어요"
        )

        whenever(chatRedisRepository.findBySessionId(sessionId)).thenReturn(state)
        whenever(chatValidationService.isClearlyInvalidSkillLevelAnswer("아직 기획을 해본적이 없어")).thenReturn(false)

        val response = chatBotUseCase.chatWithBot(
            ChatRequest(sessionId = sessionId, content = "아직 기획을 해본적이 없어")
        )

        assertEquals(6, response.nextDepth)
        assertFalse(response.completed)
        assertEquals("아직 기획을 해본적이 없어", state.skillLevel)
        assertTrue(response.message.contains("최근 직접 해본"))
    }

    @Test
    fun `현재 실력 단계에서 비응답이면 같은 질문을 다시 한다`() {
        val sessionId = "session-skill-vague"
        val state = ChatState(
            depth = 5,
            category = "직장인",
            subCategory = "기획자",
            goal = "서비스 기획자가 되고 싶어요",
            desiredOutcome = "기획서 1장을 완성하고 싶어요"
        )

        whenever(chatRedisRepository.findBySessionId(sessionId)).thenReturn(state)
        whenever(chatValidationService.isClearlyInvalidSkillLevelAnswer("글쎄")).thenReturn(true)

        val response = chatBotUseCase.chatWithBot(
            ChatRequest(sessionId = sessionId, content = "글쎄")
        )

        assertEquals(5, response.nextDepth)
        assertFalse(response.completed)
        assertNull(state.skillLevel)
        assertTrue(response.message.contains("현재 실력"))
    }

    @Test
    fun `최근 직접 해본 작업을 입력하면 목표 기간 질문으로 이동한다`() {
        val sessionId = "session-recent-work"
        val state = ChatState(
            depth = 6,
            category = "직장인",
            subCategory = "개발자",
            goal = "스프링으로 API 서버를 만들고 싶어요",
            desiredOutcome = "혼자서 실행되는 CRUD API 서버 1개를 완성하고 싶어요",
            skillLevel = "입문"
        )

        whenever(chatRedisRepository.findBySessionId(sessionId)).thenReturn(state)
        whenever(chatValidationService.isClearlyInvalidRecentExperienceAnswer(any())).thenReturn(false)

        val response = chatBotUseCase.chatWithBot(
            ChatRequest(sessionId = sessionId, content = "개인 프로젝트에서 화면 1개와 API 호출 1개는 직접 해봤어요")
        )

        assertEquals(7, response.nextDepth)
        assertFalse(response.completed)
        assertEquals("개인 프로젝트에서 화면 1개와 API 호출 1개는 직접 해봤어요", state.recentExperience)
        assertTrue(response.message.contains("기간"))
    }

    @Test
    fun `목표 기간을 입력하면 하루 투자 가능 시간 질문으로 이동한다`() {
        val sessionId = "session-period"
        val state = ChatState(
            depth = 7,
            category = "직장인",
            subCategory = "개발자",
            goal = "스프링으로 API 서버를 만들고 싶어요",
            desiredOutcome = "혼자서 실행되는 CRUD API 서버 1개를 완성하고 싶어요",
            skillLevel = "입문",
            recentExperience = "토이 프로젝트에서 API 호출 1개를 붙여봤어요"
        )

        whenever(chatRedisRepository.findBySessionId(sessionId)).thenReturn(state)
        whenever(chatValidationService.isClearlyNonAnswer(any())).thenReturn(false)

        val response = chatBotUseCase.chatWithBot(
            ChatRequest(sessionId = sessionId, content = "3개월 안에 이루고 싶어요")
        )

        assertEquals(8, response.nextDepth)
        assertFalse(response.completed)
        assertEquals("3개월 안에 이루고 싶어요", state.targetPeriod)
        assertTrue(response.message.contains("하루"))
    }

    @Test
    fun `하루 투자 가능 시간을 입력하면 추가 의견 질문으로 이동한다`() {
        val sessionId = "session-time"
        val state = ChatState(
            depth = 8,
            category = "직장인",
            subCategory = "개발자",
            goal = "스프링으로 API 서버를 만들고 싶어요",
            desiredOutcome = "혼자서 실행되는 CRUD API 서버 1개를 완성하고 싶어요",
            skillLevel = "입문",
            recentExperience = "토이 프로젝트에서 API 호출 1개를 붙여봤어요",
            targetPeriod = "3개월"
        )

        whenever(chatRedisRepository.findBySessionId(sessionId)).thenReturn(state)
        whenever(chatValidationService.isClearlyNonAnswer(any())).thenReturn(false)

        val response = chatBotUseCase.chatWithBot(
            ChatRequest(sessionId = sessionId, content = "평일에는 1시간, 주말에는 2시간 가능해요")
        )

        assertEquals(9, response.nextDepth)
        assertFalse(response.completed)
        assertEquals("평일에는 1시간, 주말에는 2시간 가능해요", state.dailyAvailableTime)
        assertTrue(response.message.contains("추가로"))
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "없음", "없어요", "딱히 없어"])
    fun `추가 의견이 없으면 바로 종료한다`(input: String) {
        val sessionId = "session-final-$input"
        val state = ChatState(
            depth = 9,
            category = "직장인",
            subCategory = "개발자",
            goal = "스프링으로 API 서버를 만들고 싶어요",
            desiredOutcome = "혼자서 실행되는 CRUD API 서버 1개를 완성하고 싶어요",
            skillLevel = "입문",
            recentExperience = "토이 프로젝트에서 API 호출 1개를 붙여봤어요",
            targetPeriod = "3개월",
            dailyAvailableTime = "1시간"
        )

        whenever(chatRedisRepository.findBySessionId(sessionId)).thenReturn(state)

        val response = chatBotUseCase.chatWithBot(
            ChatRequest(sessionId = sessionId, content = input)
        )

        assertEquals(999, response.nextDepth)
        assertTrue(response.completed)
        assertNull(state.additionalOpinion)
        assertTrue(response.message.contains("현재 목표: 스프링으로 API 서버를 만들고 싶어요"))
        assertTrue(response.message.contains("최종 결과물: 혼자서 실행되는 CRUD API 서버 1개를 완성하고 싶어요"))
        assertTrue(response.message.contains("현재 실력: 입문"))
        assertTrue(response.message.contains("최근 직접 해본 작업: 토이 프로젝트에서 API 호출 1개를 붙여봤어요"))
        assertTrue(response.message.contains("목표 기간: 3개월"))
        assertTrue(response.message.contains("하루 투자 가능 시간: 1시간"))

        verify(chatRedisRepository).deleteBySessionId(sessionId)
        verify(chatRedisRepository, never()).saveChatState(any(), any())
    }

    @Test
    fun `추가 의견까지 입력하면 내용을 포함해 종료한다`() {
        val sessionId = "session-final-opinion"
        val state = ChatState(
            depth = 9,
            category = "직장인",
            subCategory = "개발자",
            goal = "스프링으로 API 서버를 만들고 싶어요",
            desiredOutcome = "혼자서 실행되는 CRUD API 서버 1개를 완성하고 싶어요",
            skillLevel = "입문",
            recentExperience = "토이 프로젝트에서 API 호출 1개를 붙여봤어요",
            targetPeriod = "3개월",
            dailyAvailableTime = "1시간"
        )

        whenever(chatRedisRepository.findBySessionId(sessionId)).thenReturn(state)

        val response = chatBotUseCase.chatWithBot(
            ChatRequest(sessionId = sessionId, content = "실습 위주 커리큘럼이면 좋겠어요")
        )

        assertEquals(999, response.nextDepth)
        assertTrue(response.completed)
        assertEquals("실습 위주 커리큘럼이면 좋겠어요", state.additionalOpinion)
        assertTrue(response.message.contains("추가 의견: 실습 위주 커리큘럼이면 좋겠어요"))

        verify(chatRedisRepository).deleteBySessionId(sessionId)
    }
}
