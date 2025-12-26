package com.haruUp.interest.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.haruUp.global.clova.ClovaApiClient
import com.haruUp.global.clova.ClovaApiResponse
import com.haruUp.global.clova.Message
import com.haruUp.global.clova.Result
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.transaction.annotation.Transactional
import org.mockito.BDDMockito.given
import org.mockito.kotlin.any

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@Transactional
class InterestValidationIntegrationTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    /**
     * 🔥 외부 AI 호출은 반드시 Mock
     */
    @MockBean
    lateinit var clovaApiClient: ClovaApiClient

    @BeforeEach
    fun setUp() {
        // 기본적으로 AI는 "true" 반환하도록 설정
        given(clovaApiClient.chatCompletion(
            messages = any(),
            maxTokens = any(),
            temperature = any(),
            topK = any(),
            topP = any(),
            repeatPenalty = any(),
            stopBefore = any(),
            includeAiFilters = any(),
            seed = any()
        )).willReturn(
            ClovaApiResponse(
                status = null,
                result = Result(
                    message = Message(
                        role = "assistant",
                        content = "true"
                    )
                )
            )
        )
    }

    // =========================================
    // 1) 정상 문자열
    // =========================================
    @Test
    fun `관심사 검증 - 정상 문자열`() {

        val request = mapOf(
            "interest" to "근력 키우기"
        )

        val result = mockMvc.post("/api/interests/interest/validation") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }
            .andExpect {
                status { isOk() }
                jsonPath("$.success") { value(true) }
                jsonPath("$.data.isValid") { value(true) }
            }
            .andReturn()

        assertNotNull(result.response.contentAsString)
    }

    // =========================================
    // 2) 자음 반복 → 로컬 검증에서 컷
    // =========================================
    @Test
    fun `관심사 검증 - 자음 반복`() {

        val request = mapOf(
            "interest" to "ㄱㄱㄱㄱ"
        )

        mockMvc.post("/api/interests/interest/validation") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }
            .andExpect {
                status { isOk() }
                jsonPath("$.data.isValid") { value(false) }
            }
    }

    // =========================================
    // 3) 숫자 포함 → 로컬 검증에서 컷
    // =========================================
    @Test
    fun `관심사 검증 - 숫자 포함`() {

        val request = mapOf(
            "interest" to "헬스123"
        )

        mockMvc.post("/api/interests/interest/validation") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }
            .andExpect {
                status { isOk() }
                jsonPath("$.data.isValid") { value(false) }
            }
    }

    // =========================================
    // 4) AI가 false 반환하는 경우
    // =========================================
    @Test
    fun `관심사 검증 - AI 판단으로 실패`() {

        // AI 응답을 false로 변경
        given(clovaApiClient.chatCompletion(
            messages = any(),
            maxTokens = any(),
            temperature = any(),
            topK = any(),
            topP = any(),
            repeatPenalty = any(),
            stopBefore = any(),
            includeAiFilters = any(),
            seed = any()
        )).willReturn(
            ClovaApiResponse(
                status = null,
                result = Result(
                    message = Message(
                        role = "assistant",
                        content = "false"
                    )
                )
            )
        )

        val request = mapOf(
            "interest" to "근력 키우기기"
        )

        mockMvc.post("/api/interests/interest/validation") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }
            .andExpect {
                status { isOk() }
                jsonPath("$.data.isValid") { value(false) }
            }
    }
}