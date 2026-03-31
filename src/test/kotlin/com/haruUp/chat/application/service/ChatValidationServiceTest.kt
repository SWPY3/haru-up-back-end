package com.haruUp.chat.application.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.haruUp.global.clova.ClovaApiClient
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockitoExtension::class)
class ChatValidationServiceTest {

    @Mock
    lateinit var clovaApiClient: ClovaApiClient

    private val objectMapper = jacksonObjectMapper()

    @ParameterizedTest
    @ValueSource(strings = ["글쎄", "잘 모르겠는데?", "딱히 없어요", "전혀 없는데", "그냥요", "해보고 싶어서요", "애매한데요"])
    fun `모호한 답변은 non answer 로 판단한다`(input: String) {
        val service = ChatValidationService(clovaApiClient, objectMapper)

        assertTrue(service.isClearlyNonAnswer(input))
    }

    @ParameterizedTest
    @ValueSource(strings = ["회사에서 기획 문서를 자주 써서 더 잘하고 싶어요", "데이터 기반 의사결정을 배우고 싶어요", "잘 모르겠는데 데이터 분석 쪽은 흥미가 있어요"])
    fun `구체성이 있는 답변은 non answer 로 보지 않는다`(input: String) {
        val service = ChatValidationService(clovaApiClient, objectMapper)

        assertFalse(service.isClearlyNonAnswer(input))
    }

    @ParameterizedTest
    @ValueSource(strings = ["아직 기획을 해본적이 없어", "처음이야", "입문 수준이에요", "실무 경험은 없고 기초만 알아요", "웹개발만 해봤어"])
    fun `현재 실력 단계의 입문형 답변은 유효한 답변으로 본다`(input: String) {
        val service = ChatValidationService(clovaApiClient, objectMapper)

        assertFalse(service.isClearlyInvalidSkillLevelAnswer(input))
    }

    @ParameterizedTest
    @ValueSource(strings = ["글쎄", "잘 모르겠는데?", "딱히", "모르겠어"])
    fun `현재 실력 단계에서도 명백한 비응답은 다시 입력받는다`(input: String) {
        val service = ChatValidationService(clovaApiClient, objectMapper)

        assertTrue(service.isClearlyInvalidSkillLevelAnswer(input))
    }

    @ParameterizedTest
    @ValueSource(strings = ["아직 해본 적 없음", "피그마로 화면 1개 그려봤어요", "기획서 초안 1장 써봤어요", "직접 해본 건 없어요"])
    fun `최근 직접 해본 작업 답변은 유효하게 본다`(input: String) {
        val service = ChatValidationService(clovaApiClient, objectMapper)

        assertFalse(service.isClearlyInvalidRecentExperienceAnswer(input))
    }

    @ParameterizedTest
    @ValueSource(strings = ["글쎄", "모르겠어", "딱히", "아무거나"])
    fun `최근 직접 해본 작업 질문에서도 비응답은 다시 입력받는다`(input: String) {
        val service = ChatValidationService(clovaApiClient, objectMapper)

        assertTrue(service.isClearlyInvalidRecentExperienceAnswer(input))
    }
}
