package com.haruUp.chat.application.service

import com.haruUp.missionembedding.service.MissionRecommendationService
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExtendWith(MockitoExtension::class)
class ChatBotMissionRecommendationServiceTest {

    @Mock
    lateinit var missionRecommendationService: MissionRecommendationService

    @Test
    fun `완료한 오늘 미션이 있으면 다음날 추천에 연속성 문맥을 반영한다`() = runBlocking {
        whenever(
            missionRecommendationService.recommendTodayMissions(
                any(),
                any(),
                anyOrNull(),
                any(),
                anyOrNull()
            )
        ).thenReturn(emptyList())

        val service = ChatBotMissionRecommendationService(missionRecommendationService)

        service.recommend(
            ChatBotMissionContext(
                category = "직장인",
                subCategory = "개발자",
                goal = "앱 MVP를 만들고 싶어요.",
                desiredOutcome = "로그인 가능한 앱 MVP 1개를 만들고 싶어요.",
                skillLevel = "웹 개발은 해봤지만 앱 개발은 처음이에요.",
                recentExperience = "React로 화면 1개와 API 연결 1개는 해봤어요.",
                targetPeriod = "2달",
                dailyAvailableTime = "하루 2시간",
                additionalOpinion = "실습 위주면 좋겠어요.",
                completedMissions = listOf(
                    ChatBotCompletedMission(
                        content = "React Native 설치 후 Hello World 앱 생성",
                        difficulty = 1
                    ),
                    ChatBotCompletedMission(
                        content = "API 호출 예제 1개 분석 및 재현",
                        difficulty = 2
                    )
                )
            )
        )

        val pathCaptor = argumentCaptor<List<String>>()
        val excludeCaptor = argumentCaptor<List<String>>()
        val contextCaptor = argumentCaptor<String>()

        verify(missionRecommendationService).recommendTodayMissions(
            pathCaptor.capture(),
            any(),
            anyOrNull(),
            excludeCaptor.capture(),
            contextCaptor.capture()
        )

        assertEquals(listOf("직장인", "개발자"), pathCaptor.firstValue)
        assertEquals(
            listOf(
                "React Native 설치 후 Hello World 앱 생성",
                "API 호출 예제 1개 분석 및 재현"
            ),
            excludeCaptor.firstValue
        )
        assertTrue(contextCaptor.firstValue.contains("이번 추천 모드: 다음날 후속 추천"))
        assertTrue(contextCaptor.firstValue.contains("최종 결과물: 로그인 가능한 앱 MVP 1개를 만들고 싶어요."))
        assertTrue(contextCaptor.firstValue.contains("최근 직접 해본 작업: React로 화면 1개와 API 연결 1개는 해봤어요."))
        assertTrue(contextCaptor.firstValue.contains("전날 완료한 미션:"))
        assertTrue(contextCaptor.firstValue.contains("같은 난이도라도 전날 미션보다 한 단계 더 진전된 후속 미션"))
    }
}
