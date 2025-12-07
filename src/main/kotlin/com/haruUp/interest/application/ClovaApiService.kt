package com.haruUp.interest.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.core.type.TypeReference
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import java.util.UUID

@Service
class ClovaApiService(
    private val clovaRestClient: RestClient,
    @Value("\${clova.api.model-id:HCX-DASH-002}") private val modelId: String,
    private val objectMapper: ObjectMapper
) {

    companion object {
        private val logger = LoggerFactory.getLogger(ClovaApiService::class.java)

        // 상세한 시스템 프롬프트
        private const val SYSTEM_PROMPT = """당신은 사용자의 관심사를 분석하고 개인화된 추천을 제공하는 전문가입니다.
사용자의 프로필 정보(나이, 성별, 직업, 기존 관심사 등)와 상위 관심사를 종합적으로 분석하여,
해당 사용자에게 가장 적합하고 연관성 높은 하위 관심사를 정확하게 추천해야 합니다.

## 추천 규칙
1. **사용자 맞춤형**: 사용자의 나이, 성별, 직업, 라이프스타일에 적합한 관심사를 추천합니다.
2. **연관성**: 제공된 상위 관심사와 명확한 연관성이 있어야 합니다.
3. **정확한 개수**: 정확히 5개의 관심사를 추천합니다.
4. **명확성**: 각 추천 항목은 간결하고 명확해야 합니다 (2-10자).
5. **다양성**: 중복되거나 지나치게 유사한 항목은 제외하고 다양성을 유지합니다.
6. **실용성**: 사용자가 실제로 즐기거나 시작할 수 있는 현실적인 관심사를 추천합니다.

## 응답 형식
반드시 JSON 배열 형식으로만 응답하세요. 다른 설명이나 텍스트는 포함하지 마세요.

### 예시 1 - 사용자 정보를 고려한 중분류 추천
입력:
사용자 정보: 28세 여성, 직장인, 기존 관심사: 요가, 명상
관심사: 운동
응답: ["필라테스", "발레핏", "스트레칭", "바레핏", "홈트레이닝"]

### 예시 2 - 사용자 정보를 고려한 소분류 추천
입력:
사용자 정보: 35세 남성, IT 개발자, 기존 관심사: 헬스, 게임
관심사: 운동 > 헬스
응답: ["웨이트 트레이닝", "기능성 운동", "크로스핏", "바디빌딩", "파워리프팅"]

### 예시 3 - 젊은 사용자의 취미
입력:
사용자 정보: 22세 여성, 대학생, 기존 관심사: 카페투어, SNS
관심사: 취미
응답: ["사진 촬영", "베이킹", "캘리그라피", "드로잉", "플리마켓 탐방"]

### 예시 4 - 직장인 사용자의 음악 취향
입력:
사용자 정보: 30세 남성, 마케터, 기존 관심사: 독서, 카페
관심사: 취미 > 음악 감상
응답: ["인디음악", "재즈", "팝송", "클래식", "힐링음악"]

### 예시 5 - 관심사 정보 없는 경우
입력:
사용자 정보: 25세 남성, 직장인
관심사: 운동
응답: ["헬스", "축구", "농구", "러닝", "수영"]

중요:
- 반드시 JSON 배열 형식으로만 응답하세요.
- 사용자의 나이, 성별, 직업, 기존 관심사를 고려하여 가장 적합한 관심사를 추천하세요.
- 제공된 상위 관심사와 명확한 연관성이 있어야 합니다."""
    }

    /**
     * Clova API를 호출하여 관심사 추천 받기
     * @param interestPath 관심사 경로 (예: "운동 > 헬스")
     * @param userInfo 사용자 정보 (선택, 예: "30세 남성, 개발자")
     * @return 추천된 관심사 리스트
     */
    fun getRecommendedInterests(interestPath: String, userInfo: String? = null): List<String> {
        try {
            val requestId = UUID.randomUUID().toString().replace("-", "")
            logger.info("Clova API 호출 시작: requestId={}, interestPath={}, userInfo={}", requestId, interestPath, userInfo)

            // Few-shot 예시 메시지들
            val fewShotMessages = createFewShotMessages()

            // 사용자 프롬프트 생성
            val userPrompt = buildUserPrompt(interestPath, userInfo)

            // 전체 메시지 구성
            val messages = mutableListOf<ClovaMessage>()
            messages.add(createSystemMessage())
            messages.addAll(fewShotMessages)
            messages.add(createUserMessage(userPrompt))

            val request = ClovaRequestV3(
                messages = messages,
                topP = 0.8,
                topK = 0,
                maxTokens = 256,
                temperature = 0.5,
                repetitionPenalty = 1.1,
                stop = emptyList(),
                seed = 0,
                includeAiFilters = true
            )

            val response = clovaRestClient.post()
                .uri("/v3/chat-completions/$modelId")
                .header("X-NCP-CLOVASTUDIO-REQUEST-ID", requestId)
                .body(request)
                .retrieve()
                .body(ClovaResponseV3::class.java)

            logger.info("Clova API 호출 성공: requestId={}, status={}", requestId, response?.status?.code)

            // JSON 응답 파싱
            val content = response?.result?.message?.content
            if (content.isNullOrBlank()) {
                logger.warn("Clova API 응답 내용이 비어있음")
                return emptyList()
            }

            logger.info("Clova API 응답 content: {}", content)

            // JSON 배열 추출 및 파싱
            val interests = parseJsonResponse(content)

            logger.info("추출된 관심사: {}", interests)
            return interests

        } catch (e: Exception) {
            logger.error("Clova API 호출 실패", e)
            return emptyList()
        }
    }

    /**
     * 시스템 메시지 생성
     */
    private fun createSystemMessage(): ClovaMessage {
        return ClovaMessage(
            role = "system",
            content = listOf(ContentItem(type = "text", text = SYSTEM_PROMPT))
        )
    }

    /**
     * 사용자 메시지 생성
     */
    private fun createUserMessage(text: String): ClovaMessage {
        return ClovaMessage(
            role = "user",
            content = listOf(ContentItem(type = "text", text = text))
        )
    }

    /**
     * 어시스턴트 메시지 생성
     */
    private fun createAssistantMessage(text: String): ClovaMessage {
        return ClovaMessage(
            role = "assistant",
            content = listOf(ContentItem(type = "text", text = text))
        )
    }

    /**
     * Few-shot 예시 메시지 생성
     */
    private fun createFewShotMessages(): List<ClovaMessage> {
        return listOf(
            createUserMessage("직무 관련 역량 개발\n\n"),
            createAssistantMessage("""```json
[
  "프로그래밍 언어",
  "데이터 분석 스킬",
  "소프트웨어 공학",
  "문제 해결 능력",
  "리더십 및 팀워크"
]
```"""),
            createUserMessage("자격증 공부"),
            createAssistantMessage("""```json
[
  "정보처리기사",
  "컴퓨터활용능력",
  "SQLD/ADsP",
  "네트워크 관리사",
  "데이터 분석 준전문가"
]
```"""),
            createUserMessage("자격증 공부 > 정보처리기사"),
            createAssistantMessage("""```json
[
  "알고리즘 문제 풀이",
  "데이터베이스 이해",
  "네트워크 기초",
  "운영체제 개념",
  "소프트웨어 설계 원리"
]
```"""),
            createUserMessage("직무 관련 역량 개발 > 업무 능력 향상"),
            createAssistantMessage("""```json
[
  "엑셀 활용법",
  "파워포인트 기술",
  "문서 작성 스킬",
  "의사소통 능력",
  "시간 관리 방법"
]
```"""),
            createUserMessage("직무 관련 역량 개발 > 커리어 전환 준비"),
            createAssistantMessage("""```json
[
  "경력 관리 전략",
  "이직 준비 방법",
  "자기소개서 작성법",
  "면접 대비 팁",
  "커리어 목표 설정"
]
```"""),
            createUserMessage("나이: 30세\n직업: 개발자 \n관심사: 직무 관련 역량 개발 > 커리어 전환 준비\n\n"),
            createAssistantMessage("""```json
[
  "프로젝트 포트폴리오",
  "기술 블로그 운영",
  "오픈소스 기여 경험",
  "네트워킹 이벤트 참여",
  "전문 자격증 취득"
]
```""")
        )
    }

    /**
     * 사용자 프롬프트 생성
     */
    private fun buildUserPrompt(interestPath: String, userInfo: String?): String {
        return if (userInfo.isNullOrBlank()) {
            "관심사: $interestPath"
        } else {
            "$userInfo\n관심사: $interestPath\n\n"
        }
    }

    /**
     * JSON 응답 파싱
     */
    private fun parseJsonResponse(content: String): List<String> {
        return try {
            // ```json ... ``` 형식에서 JSON 추출
            val jsonPattern = Regex("```json\\s*\\n?([\\s\\S]*?)\\n?```")
            val matchResult = jsonPattern.find(content)

            val jsonText = if (matchResult != null) {
                matchResult.groupValues[1].trim()
            } else {
                // 직접 JSON 배열인 경우
                val arrayPattern = Regex("\\[([\\s\\S]*?)\\]")
                val arrayMatch = arrayPattern.find(content)
                if (arrayMatch != null) {
                    "[${arrayMatch.groupValues[1]}]"
                } else {
                    content.trim()
                }
            }

            logger.info("추출된 JSON 텍스트: {}", jsonText)

            // JSON 파싱
            objectMapper.readValue(jsonText, object : TypeReference<List<String>>() {})
        } catch (e: Exception) {
            logger.error("JSON 파싱 실패: {}", content, e)

            // 파싱 실패 시 fallback: 쉼표로 구분된 텍스트 처리
            content
                .replace(Regex("```json|```|\\[|\\]|\""), "")
                .split(Regex("[,\n]"))
                .map { it.trim() }
                .filter { it.isNotBlank() }
        }
    }
}

// Clova API V3 Request DTO
data class ClovaRequestV3(
    val messages: List<ClovaMessage>,
    val topP: Double,
    val topK: Int,
    val maxTokens: Int,
    val temperature: Double,
    val repetitionPenalty: Double,
    val stop: List<String>,
    val seed: Int,
    val includeAiFilters: Boolean
)

data class ClovaMessage(
    val role: String,
    val content: List<ContentItem>
)

data class ContentItem(
    val type: String,
    val text: String
)

// Clova API V3 Response DTO
data class ClovaResponseV3(
    val status: StatusV3?,
    val result: ResultV3?
)

data class StatusV3(
    val code: String?,
    val message: String?
)

data class ResultV3(
    val message: MessageContentV3?,
    val inputLength: Int?,
    val outputLength: Int?,
    val stopReason: String?,
    val seed: Long?,
    val aiFilter: List<AiFilterV3>?
)

data class MessageContentV3(
    val role: String?,
    val content: String?  // V3 API 응답에서는 content가 String
)

data class AiFilterV3(
    val groupName: String?,
    val name: String?,
    val score: String?,
    val result: String?
)
