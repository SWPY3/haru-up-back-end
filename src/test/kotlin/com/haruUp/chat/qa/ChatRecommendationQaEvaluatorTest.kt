package com.haruUp.chat.qa

import com.haruUp.missionembedding.dto.MissionDto
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ChatRecommendationQaEvaluatorTest {

    @Test
    fun `묶음 과제와 모호한 세트는 상세 이슈로 잡아낸다`() {
        val scenario = scenario(
            id = "planner_test",
            title = "기획 입문",
            category = "직장인",
            subCategory = "기획자",
            expectation = ChatRecommendationQaExpectation(
                todayRequiredGroups = listOf(
                    keywords("사용자", "문제", "요구사항"),
                    keywords("기획", "흐름", "화면", "와이어프레임")
                ),
                nextDayRequiredGroups = listOf(
                    keywords("사용자", "문제", "요구사항"),
                    keywords("기획", "흐름", "화면", "와이어프레임")
                )
            )
        )

        val evaluation = ChatRecommendationQaEvaluator.evaluate(
            scenario = scenario,
            todayMissions = listOf(
                mission("서비스 아이디어 3개 작성 + 간략한 설명 추가", 1),
                mission("아이디어 1개 선택 후 페르소나 1명 설정하고 필요한 기능 3가지 적기", 2),
                mission("핵심 기능 3개 구조화하고 우선순위 매기기", 3),
                mission("핵심 기능 기반 화면 1개 스케치하기", 4),
                mission("스케치한 화면의 기술적 구현 가능성 조사 후 200자 요약", 5)
            ),
            nextDayMissions = listOf(
                mission("기획 관련 서적 1챕터 읽고 핵심 포인트 3개 메모", 1),
                mission("인기 앱 1개 선정해 기획서 구조 분석 후 요약", 2),
                mission("타겟 시장의 최근 트렌드 3개 조사 후 기록", 3),
                mission("가상 프로젝트 설정 후 초기 기획안 A4 1장 분량 작성", 4),
                mission("서비스 기획 포트폴리오 목차 초안 5개 섹션 작성", 5)
            )
        )

        assertTrue(evaluation.issues.any { it.contains("여러 행동/산출물") })
        assertTrue(evaluation.issues.any { it.contains("직접 연결되지 않는 미션") })
        assertTrue(!evaluation.passed)
    }

    @Test
    fun `다음날 추천이 다른 산출물 축으로 튀면 연속성 이슈를 남긴다`() {
        val scenario = scenario(
            id = "career_test",
            title = "개발자 이직 준비",
            category = "직장인",
            subCategory = "개발자",
            expectation = ChatRecommendationQaExpectation(
                todayRequiredGroups = listOf(
                    keywords("프로젝트", "성과", "github", "readme"),
                    keywords("이력서", "면접", "포트폴리오")
                ),
                nextDayRequiredGroups = listOf(
                    keywords("프로젝트", "성과", "github", "readme"),
                    keywords("이력서", "면접", "포트폴리오")
                )
            )
        )

        val evaluation = ChatRecommendationQaEvaluator.evaluate(
            scenario = scenario,
            todayMissions = listOf(
                mission("대표 프로젝트 1개 문제 해결 300자 정리", 1),
                mission("README 핵심 성과 섹션 1개 보강", 2),
                mission("이력서 bullet 3개 수치 중심으로 수정", 3),
                mission("면접 답변 3개 STAR 형식으로 정리", 4),
                mission("포트폴리오 프로젝트 소개 문단 1개 다듬기", 5)
            ),
            nextDayMissions = listOf(
                mission("트렌드 리포트 1개 읽고 핵심 3줄 메모", 1),
                mission("업계 뉴스 3개 요약", 2),
                mission("인기 서비스 1개 장단점 표 작성", 3),
                mission("가상 서비스 아이디어 1개 기획", 4),
                mission("브랜드 슬로건 3개 초안 작성", 5)
            )
        )

        assertTrue(evaluation.issues.any { it.contains("주제 연속성이 약합니다") })
        assertTrue(
            evaluation.issues.any { issue ->
                issue.contains("산출물 연속성이 약합니다") || issue.contains("직접 연결되지 않는 미션")
            }
        )
        assertTrue(!evaluation.passed)
    }

    private fun scenario(
        id: String,
        title: String,
        category: String,
        subCategory: String,
        expectation: ChatRecommendationQaExpectation
    ): ChatRecommendationQaScenario {
        return ChatRecommendationQaScenario(
            id = id,
            title = title,
            category = category,
            subCategory = subCategory,
            goal = "테스트 목표",
            desiredOutcome = "테스트 결과물",
            skillLevel = "테스트 실력",
            recentExperience = "테스트 경험",
            targetPeriod = "3개월",
            dailyAvailableTime = "하루 2시간",
            expectation = expectation
        )
    }

    private fun keywords(vararg values: String): Set<String> = values.map { it.lowercase() }.toSet()

    private fun mission(content: String, difficulty: Int): MissionDto {
        return MissionDto(
            content = content,
            difficulty = difficulty
        )
    }
}
