package com.haruUp.domain.interest.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.haruUp.domain.interest.entity.InterestEmbeddingEntity
import com.haruUp.domain.interest.model.InterestLevel
import com.haruUp.domain.interest.model.InterestNode
import com.haruUp.domain.interest.model.InterestPath
import com.haruUp.global.clova.ClovaApiClient
import com.haruUp.global.clova.UserProfile
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.*

/**
 * AI 기반 관심사 추천 서비스
 *
 * RAG로 충분히 추천하지 못할 때 AI가 추가 추천
 */
@Service
class AIInterestRecommender(
    private val clovaApiClient: ClovaApiClient,
    private val interestRepository: com.haruUp.domain.interest.repository.InterestRepository,
    private val embeddingRepository: com.haruUp.domain.interest.repository.InterestEmbeddingJpaRepository
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val objectMapper = jacksonObjectMapper()

    /**
     * AI 추천
     */
    fun recommend(
        selectedInterests: List<InterestPath>,
        currentLevel: InterestLevel,
        excludeNames: List<String>,
        count: Int,
        userProfile: UserProfile
    ): List<InterestNode> {
        logger.info("AI 추천 시작 - 레벨: $currentLevel, 개수: $count")

        val prompt = buildPrompt(
            selectedInterests = selectedInterests,
            currentLevel = currentLevel,
            excludeNames = excludeNames,
            count = count,
            userProfile = userProfile
        )

        try {
            val response = clovaApiClient.generateText(
                userMessage = prompt,
                systemMessage = SYSTEM_PROMPT
            )

            val recommendations = parseResponse(
                response = response,
                level = currentLevel,
                selectedInterests = selectedInterests
            )
            logger.info("AI 추천 성공: ${recommendations.size}개")

            return recommendations

        } catch (e: Exception) {
            logger.error("AI 추천 실패: ${e.message}", e)
            return emptyList()
        }
    }

    /**
     * 프롬프트 생성
     */
    private fun buildPrompt(
        selectedInterests: List<InterestPath>,
        currentLevel: InterestLevel,
        excludeNames: List<String>,
        count: Int,
        userProfile: UserProfile
    ): String {
        val sb = StringBuilder()

        // 사용자 정보
        sb.appendLine("사용자 정보: ${formatUserProfile(userProfile)}")

        // 계층 구조 설명 및 컨텍스트
        if (selectedInterests.isNotEmpty()) {
            sb.appendLine("\n사용자가 선택한 관심사:")
            selectedInterests.forEach { path ->
                sb.appendLine("- ${path.toPathString()}")
            }

            // 현재 레벨과 부모 관계 명확히 전달
            val firstPath = selectedInterests.first()
            when (currentLevel) {
                InterestLevel.MAIN -> {
                    sb.appendLine("\n추천 요청: 대분류 관심사")
                    sb.appendLine("예시: 운동, 공부, 취미생활, 자기계발, 사회활동 등")
                }
                InterestLevel.MIDDLE -> {
                    sb.appendLine("\n추천 요청: '${firstPath.mainCategory}'의 중분류 관심사")
                    sb.appendLine("예시: ${getMiddleExamples(firstPath.mainCategory)}")
                }
                InterestLevel.SUB -> {
                    val parentPath = if (firstPath.middleCategory != null) {
                        "${firstPath.mainCategory} > ${firstPath.middleCategory}"
                    } else {
                        firstPath.mainCategory
                    }
                    sb.appendLine("\n추천 요청: '${parentPath}'의 소분류(세부 활동) 관심사")
                    sb.appendLine("중요: 소분류는 구체적인 활동이나 목표여야 합니다.")
                    sb.appendLine("예시: ${getSubExamples(firstPath.mainCategory, firstPath.middleCategory)}")
                }
            }
        } else {
            sb.appendLine("\n추천 요청: ${currentLevel.description}")
        }

        // 제외할 항목
        if (excludeNames.isNotEmpty()) {
            sb.appendLine("\n제외할 항목 (절대 추천하지 말 것):")
            excludeNames.forEach { sb.appendLine("- $it") }
        }

        // 요청 사항
        sb.appendLine("\n정확히 ${count}개만 추천해주세요.")

        return sb.toString()
    }

    /**
     * 중분류 예시
     */
    private fun getMiddleExamples(mainCategory: String): String {
        return when (mainCategory) {
            "운동" -> "헬스, 요가, 필라테스, 수영, 구기종목 등"
            "공부" -> "외국어 공부, 자격증 공부, 재테크/투자 공부, IT 공부 등"
            else -> "해당 대분류의 구체적인 하위 카테고리"
        }
    }

    /**
     * 소분류 예시
     */
    private fun getSubExamples(mainCategory: String, middleCategory: String?): String {
        val examples = when {
            mainCategory == "운동" && middleCategory == "헬스" ->
                "근력 키우기, 체력 강화, 다이어트, 근육량 증가, 체형 교정, 코어 강화, 스트레칭 등"
            mainCategory == "공부" && middleCategory == "영어" ->
                "단어 학습, 문법 공부, 회화 연습, TOEIC 준비, 영어 독해 등"
            mainCategory == "공부" && middleCategory == "금융지식 쌓기" ->
                "주식 투자 배우기, ETF 학습, 부동산 공부, 재무제표 분석, 경제 뉴스 읽기 등"
            else -> "구체적인 활동이나 세부 목표"
        }

        // 소분류 특징 강조
        val characteristics = when {
            mainCategory == "운동" && middleCategory == "헬스" ->
                "\n주의: 요가, 필라테스, 러닝, 수영, 사이클링, 등산 등은 중분류이므로 절대 추천하지 마세요!"
            else -> ""
        }

        return examples + characteristics
    }

    /**
     * 응답 파싱 및 DB 저장
     */
    private fun parseResponse(
        response: String,
        level: InterestLevel,
        selectedInterests: List<InterestPath>
    ): List<InterestNode> {
        return try {
            val jsonResponse = objectMapper.readValue<AIRecommendationResponse>(response.trim())

            jsonResponse.interest.mapNotNull { name ->
                val node = InterestNode(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    level = level,
                    isEmbedded = false,
                    isUserGenerated = false,
                    usageCount = 0,
                    createdAt = LocalDateTime.now()
                )

                var parentName: String? = null
                var parentId: String? = null

                // 부모 정보 설정
                if (selectedInterests.isNotEmpty()) {
                    val firstPath = selectedInterests.first()

                    when (level) {
                        InterestLevel.MAIN -> {
                            // 대분류는 부모 없음
                            parentName = null
                        }
                        InterestLevel.MIDDLE -> {
                            // 중분류의 부모는 대분류
                            parentName = firstPath.mainCategory
                        }
                        InterestLevel.SUB -> {
                            // 소분류의 부모는 "대분류 > 중분류" 전체 경로
                            if (firstPath.middleCategory != null) {
                                parentName = "${firstPath.mainCategory} > ${firstPath.middleCategory}"
                            } else {
                                parentName = firstPath.mainCategory
                            }
                        }
                    }

                    // parentName으로부터 parentId 조회
                    parentName?.let { pName ->
                        embeddingRepository.findByFullPath(pName)?.let {
                            parentId = it.id.toString()
                        }
                    }
                }

                node.parentName = parentName

                // DB에 저장하지 않고 메모리에서만 생성하여 반환
                node
            }
        } catch (e: Exception) {
            logger.error("AI 응답 파싱 실패: ${e.message}")
            emptyList()
        }
    }

    private fun formatUserProfile(profile: UserProfile): String {
        val parts = mutableListOf<String>()
        profile.age?.let { parts.add("${it}세") }
        profile.gender?.let { parts.add(it) }
        profile.occupation?.let { parts.add(it) }
        return parts.joinToString(", ")
    }

    private data class AIRecommendationResponse(
        val interest: List<String>
    )

    companion object {
        private const val SYSTEM_PROMPT = """
당신은 사용자의 관심사를 분석하고 개인화된 추천을 제공하는 전문가입니다.
사용자의 프로필 정보와 이미 선택한 관심사를 종합적으로 분석하여,
해당 사용자에게 가장 적합하고 연관성 높은 관심사를 정확하게 추천해야 합니다.

## 추천 규칙
1. **사용자 맞춤형**: 사용자의 나이, 성별, 직업에 적합한 관심사를 추천합니다.
2. **연관성**: 제공된 관심사와 명확한 연관성이 있어야 합니다.
3. **중복 제거**: 제외 목록에 있는 항목은 절대 추천하지 않습니다.
4. **정확한 개수**: 요청된 개수만큼 정확히 추천합니다.
5. **명확성**: 각 추천 항목은 간결하고 명확해야 합니다 (2-10자).
6. **다양성**: 비슷한 항목은 피하고 다양하게 추천합니다.

## 응답 형식
반드시 JSON 객체 형식으로만 응답하세요.
형식: {"interest": ["항목1", "항목2", "항목3", ...]}

중요:
- 반드시 {"interest": [...]} 형식의 JSON 객체로만 응답하세요.
- 제외 목록의 항목은 절대 추천하지 마세요.
- 요청된 개수만큼 정확히 추천하세요.
"""
    }
}
