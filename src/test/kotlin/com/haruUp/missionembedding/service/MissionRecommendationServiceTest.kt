package com.haruUp.missionembedding.service

import com.haruUp.global.clova.ClovaApiClient
import com.haruUp.global.clova.ImprovedMissionRecommendationPrompt
import com.haruUp.global.clova.MissionMemberProfile
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever

@ExtendWith(MockitoExtension::class)
class MissionRecommendationServiceTest {

    @Mock
    lateinit var clovaApiClient: ClovaApiClient

    @Test
    fun `앱 제작 목표 문맥에서는 단계형 미션 세트를 반환한다`() = runBlocking {
        val missions = createService().recommendTodayMissions(
            directFullPath = listOf("직장인", "개발자"),
            memberProfile = MissionMemberProfile(jobName = "직장인", jobDetailName = "개발자"),
            additionalContext = """
                현재 목표: 앱을 개발하고 싶어
                최종 결과물: 실제 사용자들이 사용하는 앱을 만들고 싶어
                현재 실력: 실무에서 유지보수 개발자로 일을 하고 있지만 앱쪽은 아직 경험이 없어
                최근 직접 해본 작업: 강의만 살짝 들었어
                목표 기간: 5개월 정도 생각하고 있어
                하루 투자 가능 시간: 3시간 정도 투자 할수 있어
            """.trimIndent()
        )

        assertMissionContents(
            missions,
            listOf(
                "앱 아이디어 1개 사용자 문제 3줄 작성",
                "앱 프로젝트 1개 생성 결과 메모 3줄 작성",
                "핵심 데이터 모델 1개 필드 5개 작성",
                "핵심 기능 리스트 5개 우선순위 작성",
                "핵심 API 엔드포인트 1개 요청 응답 명세 작성"
            )
        )
    }

    @Test
    fun `앱 제작 다음날 추천은 전날 산출물의 다음 단계로 이어진다`() = runBlocking {
        val today = createService().recommendTodayMissions(
            directFullPath = listOf("직장인", "개발자"),
            memberProfile = MissionMemberProfile(jobName = "직장인", jobDetailName = "개발자"),
            additionalContext = """
                현재 목표: 앱을 개발하고 싶어
                최종 결과물: 실제 사용자들이 사용하는 앱을 만들고 싶어
                현재 실력: 앱은 처음이야
                최근 직접 해본 작업: 강의만 살짝 들었어
                목표 기간: 5개월
                하루 투자 가능 시간: 3시간
            """.trimIndent()
        )
        val nextDay = createService().recommendTodayMissions(
            directFullPath = listOf("직장인", "개발자"),
            memberProfile = MissionMemberProfile(jobName = "직장인", jobDetailName = "개발자"),
            additionalContext = """
                현재 목표: 앱을 개발하고 싶어
                최종 결과물: 실제 사용자들이 사용하는 앱을 만들고 싶어
                현재 실력: 앱은 처음이야
                최근 직접 해본 작업: 강의만 살짝 들었어
                목표 기간: 5개월
                하루 투자 가능 시간: 3시간
                이번 추천 모드: 다음날 후속 추천
                전날 완료한 미션:
                - 난이도 1: ${today[0].content}
                - 난이도 2: ${today[1].content}
                - 난이도 3: ${today[2].content}
                - 난이도 4: ${today[3].content}
                - 난이도 5: ${today[4].content}
            """.trimIndent()
        )

        assertMissionContents(
            nextDay,
            listOf(
                "앱 프로젝트 폴더 구조 5개 항목 작성",
                "핵심 데이터 테이블 1개 생성",
                "핵심 API 엔드포인트 1개 구현",
                "핵심 화면 1개 데이터 연결 구현",
                "로그인 API 1개 테스트 체크리스트 5개 작성"
            )
        )
        assertTrue(nextDay.map { it.content }.intersect(today.map { it.content }.toSet()).isEmpty())
    }

    @Test
    fun `이직 포트폴리오 문맥에서는 경력 정리 중심 미션 세트를 반환한다`() = runBlocking {
        val missions = createService().recommendTodayMissions(
            directFullPath = listOf("직장인", "개발자"),
            memberProfile = MissionMemberProfile(jobName = "직장인", jobDetailName = "개발자"),
            additionalContext = """
                현재 목표: 서비스 회사에 이직을 하고 싶어
                최종 결과물: 이직 관련해서 포트폴리오를 만들고 싶어
                현재 실력: 지금 3년차 개발자로 현업에서 일하고 있어
                최근 직접 해본 작업: 현재 현업에서 유지보수 개발자로 3년동안 일을 하고 있어
                목표 기간: 3개월 정도 생각하고 있어
                하루 투자 가능 시간: 3시간 정도 투자 할 계획이야
            """.trimIndent()
        )

        assertMissionContents(
            missions,
            listOf(
                "대표 프로젝트 1개 선정 이유 3줄 작성",
                "선정한 프로젝트 문제 해결 사례 1개를 4문장으로 작성",
                "선정한 프로젝트 README 소개 문단 1개 작성",
                "이력서 성과 bullet 3개 작성",
                "지원 직무 예상 면접 답변 1개 초안 작성"
            )
        )
    }

    @Test
    fun `백엔드 취업 문맥에서는 백엔드 포트폴리오용 미션 세트를 반환한다`() = runBlocking {
        val missions = createService().recommendTodayMissions(
            directFullPath = listOf("취준생", "개발자"),
            memberProfile = MissionMemberProfile(jobName = "취준생", jobDetailName = "개발자"),
            additionalContext = """
                현재 목표: 스프링 백엔드 직무로 취업하고 싶어
                최종 결과물: 지원할 때 보여줄 백엔드 프로젝트 포트폴리오를 만들고 싶어
                현재 실력: 학원에서 스프링 기초는 배웠고 개인 프로젝트는 아직 미완성인 수준이야
                최근 직접 해본 작업: 게시판 CRUD랑 로그인 기능 정도는 구현해봤어
                목표 기간: 4개월 안에 원서 넣을 수준까지 만들고 싶어
                하루 투자 가능 시간: 하루 2시간 정도는 쓸 수 있어
            """.trimIndent()
        )

        assertMissionContents(
            missions,
            listOf(
                "백엔드 프로젝트 API 1개 요청 응답 예시 작성",
                "백엔드 프로젝트 엔드포인트 1개 예외 처리 기준 3개 작성",
                "백엔드 프로젝트 DB 테이블 1개 역할 설명 3줄 작성",
                "백엔드 프로젝트 README 소개 문단 1개 작성",
                "백엔드 이력서 bullet 3개 작성"
            )
        )
    }

    @Test
    fun `기획 입문 문맥에서는 문제 정의에서 와이어프레임까지 이어진다`() = runBlocking {
        val missions = createService().recommendTodayMissions(
            directFullPath = listOf("직장인", "기획자"),
            memberProfile = MissionMemberProfile(jobName = "직장인", jobDetailName = "기획자"),
            additionalContext = """
                현재 목표: 내가 떠올린 서비스를 기획 문서로 정리해보고 싶어
                최종 결과물: 사용자 흐름과 요구사항이 담긴 기획 초안을 만들고 싶어
                현재 실력: 아직은 비전공자 입문 단계야
                최근 직접 해본 작업: 서비스 화면 캡처 보면서 기능 분석 메모 정도는 해봤어
                목표 기간: 4개월 안에 기획 기본기를 익히고 싶어
                하루 투자 가능 시간: 하루 1시간에서 1시간 반 정도 가능해
            """.trimIndent()
        )

        assertMissionContents(
            missions,
            listOf(
                "사용자 문제 1개 정의 문장 3개 작성",
                "핵심 사용자 페르소나 1명 초안 작성",
                "핵심 기능 1개 요구사항 3개 작성",
                "사용자 흐름 1개 4단계 작성",
                "화면 1개 와이어프레임 초안 작성"
            )
        )
    }

    @Test
    fun `사무직 문맥에서는 엑셀 정리 중심 미션 세트를 반환한다`() = runBlocking {
        val missions = createService().recommendTodayMissions(
            directFullPath = listOf("직장인", "사무직"),
            memberProfile = MissionMemberProfile(jobName = "직장인", jobDetailName = "사무직"),
            additionalContext = """
                현재 목표: 보고서 만들 때 매번 헤매는 시간을 줄이고 싶어
                최종 결과물: 보고서용 엑셀 구조와 정리 기준을 만들고 싶어
                현재 실력: 엑셀은 중급 아래 정도라고 생각해
                최근 직접 해본 작업: 월간 실적 정리와 피벗 테이블 기초 정도는 해봤어
                목표 기간: 3개월 안에 업무 시간이 줄었으면 좋겠어
                하루 투자 가능 시간: 하루 1시간 정도 가능해
            """.trimIndent()
        )

        assertMissionContents(
            missions,
            listOf(
                "반복 업무 1개 항목 5개 작성",
                "엑셀 시트 구조 3열 작성",
                "함수 1개 적용 셀 3곳 작성",
                "피벗 기준 3개 작성",
                "보고서 템플릿 규칙 5개 작성"
            )
        )
    }

    @Test
    fun `교육자 문맥에서는 수업 자료 보완 중심 미션 세트를 반환한다`() = runBlocking {
        val missions = createService().recommendTodayMissions(
            directFullPath = listOf("직장인", "교육 종사자"),
            memberProfile = MissionMemberProfile(jobName = "직장인", jobDetailName = "교육 종사자"),
            additionalContext = """
                현재 목표: 수업 준비와 피드백 시간을 줄이고 싶어
                최종 결과물: 재사용 가능한 수업 자료와 피드백 틀을 만들고 싶어
                현재 실력: 실무는 익숙하지만 온라인 콘텐츠 설계는 약해
                최근 직접 해본 작업: 활동지, 간단한 퀴즈, 학생 발표 피드백은 해봤어
                목표 기간: 2개월 반 안에 효과를 보고 싶어
                하루 투자 가능 시간: 하루 1시간 정도 가능해
            """.trimIndent()
        )

        assertMissionContents(
            missions,
            listOf(
                "수업 목표 1개 3문장 작성",
                "학습자료 핵심 개념 3개 작성",
                "활동지 문항 3개 작성",
                "퀴즈 3문항 작성",
                "피드백 루브릭 기준 4개 작성"
            )
        )
    }

    @Test
    fun `연구직 문맥에서는 연구 정리 중심 미션 세트를 반환한다`() = runBlocking {
        val missions = createService().recommendTodayMissions(
            directFullPath = listOf("학생", "연구직"),
            memberProfile = MissionMemberProfile(jobName = "학생", jobDetailName = "연구직"),
            additionalContext = """
                현재 목표: 대학원 지원 전에 연구 주제를 정리하고 싶어
                최종 결과물: 연구계획서와 발표에 바로 넣을 핵심 정리본을 만들고 싶어
                현재 실력: 실험은 해봤지만 논문 정리와 결과 문장화는 아직 익숙하지 않아
                최근 직접 해본 작업: 논문 몇 편 읽고 실험 데이터 표 정도는 만들어봤어
                목표 기간: 3개월 안에 지원 준비를 시작하고 싶어
                하루 투자 가능 시간: 하루 2시간 정도 가능해
            """.trimIndent()
        )

        assertMissionContents(
            missions,
            listOf(
                "연구 질문 1개 3문장 요약",
                "논문 1편 핵심 가설 2개 작성",
                "데이터 결과 3개 문장 작성",
                "그래프 해석 문단 1개 작성",
                "결과 요약 문단 1개 작성"
            )
        )
    }

    @Test
    fun `느슨한 JSON 응답도 content와 difficulty를 추출해 미션으로 복구한다`() = runBlocking {
        whenever(
            clovaApiClient.generateText(
                any(),
                eq(ImprovedMissionRecommendationPrompt.SYSTEM_PROMPT),
                any(),
                any(),
                anyOrNull()
            )
        ).thenReturn(
            """
            아래처럼 정리했어요.
            ```json
            {
              "missions": [
                {"difficulty": 3, "content": "핵심 기능 리스트 5개 작성"},
                {"difficulty": 1, "content": "앱 아이디어 1개 사용자 문제 3줄 작성"},
                {"difficulty": 5, "content": "핵심 API 엔드포인트 1개 요청 응답 명세 작성"},
                {"difficulty": 2, "content": "앱 프로젝트 1개 생성 결과 메모 3줄 작성"},
                {"difficulty": 4, "content": "핵심 데이터 모델 1개 필드 5개 작성"}
              ]
            }
            ```
            """.trimIndent()
        )

        val missions = createService().recommendTodayMissions(
            directFullPath = listOf("기타", "일반"),
            memberProfile = MissionMemberProfile(jobName = "기타", jobDetailName = "일반")
        )

        assertMissionContents(
            missions.sortedBy { it.difficulty },
            listOf(
                "앱 아이디어 1개 사용자 문제 3줄 작성",
                "앱 프로젝트 1개 생성 결과 메모 3줄 작성",
                "핵심 기능 리스트 5개 작성",
                "핵심 데이터 모델 1개 필드 5개 작성",
                "핵심 API 엔드포인트 1개 요청 응답 명세 작성"
            )
        )
    }

    private fun createService(): MissionRecommendationService = MissionRecommendationService(clovaApiClient)

    private fun assertMissionContents(
        missions: List<com.haruUp.missionembedding.dto.MissionDto>,
        expectedContents: List<String>
    ) {
        assertEquals(expectedContents, missions.sortedBy { it.difficulty }.map { it.content })
        assertEquals(listOf(1, 2, 3, 4, 5), missions.sortedBy { it.difficulty }.map { it.difficulty })
    }
}
