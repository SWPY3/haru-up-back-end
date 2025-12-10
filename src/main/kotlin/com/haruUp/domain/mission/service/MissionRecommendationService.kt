package com.haruUp.domain.mission.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.haruUp.interest.model.InterestPath
import com.haruUp.domain.mission.dto.MissionDto
import com.haruUp.global.clova.ClovaApiClient
import com.haruUp.global.clova.ImprovedMissionRecommendationPrompt
import com.haruUp.global.clova.MissionUserProfile
import com.haruUp.global.clova.UserInterests
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * 미션 추천 서비스
 *
 * RAG + AI 하이브리드 방식으로 미션 추천
 * 1. 먼저 임베딩된 미션에서 유사한 미션 검색 (RAG)
 * 2. 부족하면 Clova API로 새로운 미션 생성 (AI)
 * 3. 생성된 미션의 임베딩 저장은 미션 선택 API에서 처리
 */
@Service
class MissionRecommendationService(
    private val clovaApiClient: ClovaApiClient,
    private val missionEmbeddingService: MissionEmbeddingService
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val objectMapper = jacksonObjectMapper()

    companion object {
        private const val TARGET_MISSION_COUNT = 5  // 각 관심사당 목표 미션 개수
        private const val RAG_MISSION_COUNT = 3     // RAG로 가져올 미션 개수
    }

    /**
     * 관심사별로 미션 5개씩 추천
     *
     * @param interests 사용자가 선택한 관심사 목록 (seqNo, InterestPath, difficulty 포함)
     * @param userProfile 사용자 프로필
     * @return 추천된 미션 목록 (각 관심사당 5개)
     */
    suspend fun recommendMissions(
        interests: List<Triple<Int?, InterestPath, Int?>>,  // (seqNo, InterestPath, difficulty)
        userProfile: MissionUserProfile
    ): List<MissionDto> {
        logger.info("미션 추천 시작 - 관심사 개수: ${interests.size}")

        val allMissions = mutableListOf<MissionDto>()

        // 각 관심사에 대해 개별적으로 미션 5개씩 추천
        for ((seqNo, interestPath, difficulty) in interests) {
            logger.info("처리 중: seqNo=$seqNo, path=${interestPath.toPathString()}, difficulty=$difficulty")

            try {
                val missions = recommendMissionsForSingleInterest(
                    interestPath = interestPath,
                    userProfile = userProfile,
                    difficulty = difficulty
                )

                // seqNo와 difficulty를 포함해서 DTO 생성
                val missionsWithSeqNo = missions.map { mission ->
                    MissionDto(
                        seqNo = seqNo,
                        content = mission.content,
                        relatedInterest = mission.relatedInterest,
                        difficulty = difficulty
                    )
                }

                allMissions.addAll(missionsWithSeqNo)
                logger.info("seqNo=$seqNo 미션 추천 완료: ${missions.size}개")

            } catch (e: Exception) {
                logger.error("seqNo=$seqNo 미션 추천 실패: ${e.message}", e)
                // 실패한 경우 빈 리스트 추가하지 않고 continue
            }
        }

        logger.info("전체 미션 추천 완료: ${allMissions.size}개")
        return allMissions
    }

    /**
     * 단일 관심사에 대해 미션 5개 추천 (RAG + AI 하이브리드)
     */
    private suspend fun recommendMissionsForSingleInterest(
        interestPath: InterestPath,
        userProfile: MissionUserProfile,
        difficulty: Int?
    ): List<Mission> {
        val interestPathString = interestPath.toPathString()
        val allMissions = mutableListOf<Mission>()

        // 1. RAG: 임베딩된 미션에서 유사한 미션 검색
        logger.info("RAG 검색 시작: $interestPathString, difficulty=$difficulty")
        val ragMissions = try {
            val userProfileString = buildUserProfileString(userProfile)
            val embeddedMissions = missionEmbeddingService.findSimilarMissions(
                mainCategory = interestPath.mainCategory,
                middleCategory = interestPath.middleCategory,
                subCategory = interestPath.subCategory,
                difficulty = difficulty,
                userProfile = userProfileString,
                limit = RAG_MISSION_COUNT
            )

            embeddedMissions.map { entity ->
                Mission(
                    content = entity.missionContent,
                    relatedInterest = entity.getInterestPath()
                )
            }
        } catch (e: Exception) {
            logger.warn("RAG 검색 실패: ${e.message}")
            emptyList()
        }

        allMissions.addAll(ragMissions)
        logger.info("RAG로 ${ragMissions.size}개 미션 가져옴")

        // 2. AI: 부족한 경우 Clova API로 생성
        val remainingCount = TARGET_MISSION_COUNT - allMissions.size
        if (remainingCount > 0) {
            logger.info("AI로 $remainingCount 개 미션 생성 필요")

            val aiMissions = generateMissionsWithAI(
                interestPath = interestPath,
                userProfile = userProfile,
                difficulty = difficulty,
                count = remainingCount
            )

            // AI로 생성한 미션은 추천만 하고, 임베딩 저장은 미션 선택 API에서 처리
            allMissions.addAll(aiMissions)
            logger.info("AI로 ${aiMissions.size}개 미션 생성 완료")
        }

        return allMissions.take(TARGET_MISSION_COUNT)
    }

    /**
     * AI로 미션 생성
     */
    private suspend fun generateMissionsWithAI(
        interestPath: InterestPath,
        userProfile: MissionUserProfile,
        difficulty: Int?,
        count: Int
    ): List<Mission> {
        // domain.interest.model.InterestPath -> global.clova.InterestPath 변환
        val clovaInterestPath = com.haruUp.global.clova.InterestPath(
            mainCategory = interestPath.mainCategory,
            middleCategory = interestPath.middleCategory,
            subCategory = interestPath.subCategory
        )

        // 단일 관심사를 UserInterests로 변환
        val userInterests = UserInterests(listOf(clovaInterestPath))

        // 프롬프트 생성 (난이도에 따라 다르게)
        val basePrompt = ImprovedMissionRecommendationPrompt.createUserMessageForAllInterests(
            userInterests = userInterests,
            missionUserProfile = userProfile,
            focusInterest = null
        )

        val userMessage = if (difficulty == null) {
            // difficulty가 없으면 정량적 수치 없는 미션
            """
$basePrompt

**생성할 미션 개수: $count 개**

**중요: 미션 형식 요구사항**
정량적 수치나 구체적인 횟수, 시간, 개수를 포함하지 마세요.
일반적이고 자유로운 형태의 미션을 추천해주세요.

예시:
- "영어 단어 공부하기" (O)
- "하루 10개 영단어 암기하기" (X - 수치 포함)
- "헬스장에서 운동하기" (O)
- "주 3회 30분 운동하기" (X - 수치 포함)
            """.trim()
        } else {
            // difficulty가 있으면 수치 포함 미션
            val difficultyDescription = getDifficultyDescription(difficulty)
            """
$basePrompt

**생성할 미션 개수: $count 개**

**난이도 요구사항:**
$difficultyDescription

**중요: 미션 형식 요구사항**
반드시 검증 가능한 정량적 수치를 포함해주세요. 모호하거나 측정하기 어려운 표현은 피해주세요.

**올바른 예시 (구체적이고 측정 가능):**
- "주 3회 30분 운동하기" (O)
- "하루 단백질 100g 섭취하기" (O)
- "하루 10개 영단어 암기하기" (O)
- "5km 달리기" (O)

**잘못된 예시 (모호하고 측정 불가):**
- "단백질 섭취량 챙겨먹기" (X - 몇 g인지 불명확)
- "충분한 운동하기" (X - 얼마나?)
- "열심히 공부하기" (X - 시간/분량 불명확)

반드시 횟수, 시간, 개수, 거리, 그램(g), 페이지 등 구체적인 수치를 포함해주세요.
난이도에 맞는 명확하고 측정 가능한 미션을 추천해주세요.
            """.trim()
        }

        logger.debug("Clova API 호출: $userMessage")

        // Clova API 호출
        val response = clovaApiClient.generateText(
            userMessage = userMessage,
            systemMessage = ImprovedMissionRecommendationPrompt.SYSTEM_PROMPT
        )

        logger.debug("Clova API 응답: $response")

        // JSON 파싱
        return parseMissionResponse(response).take(count)
    }

    /**
     * 사용자 프로필을 문자열로 변환
     */
    private fun buildUserProfileString(userProfile: MissionUserProfile): String {
        val parts = mutableListOf<String>()
        userProfile.age?.let { parts.add("${it}세") }
        userProfile.introduction?.let { parts.add(it) }
        return parts.joinToString(", ").ifEmpty { "프로필 정보 없음" }
    }

    /**
     * 난이도를 설명으로 변환
     */
    private fun getDifficultyDescription(difficulty: Int): String {
        return when (difficulty) {
            1 -> """
                난이도 1 (중학생 수준): 매우 쉽고 기초적인 미션
                - 적은 시간 투자 (5-10분)
                - 작은 목표 수치 (예: 하루 5개, 10분, 1회)
                - 누구나 쉽게 시작할 수 있는 수준
            """.trimIndent()
            2 -> """
                난이도 2 (고등학생 수준): 기본적인 지식이나 기술이 필요한 미션
                - 중간 시간 투자 (15-20분)
                - 중간 목표 수치 (예: 하루 10개, 20분, 주 2회)
                - 약간의 노력이 필요
            """.trimIndent()
            3 -> """
                난이도 3 (대학생 수준): 중급 수준의 미션
                - 상당한 시간 투자 (30분-1시간)
                - 상당한 목표 수치 (예: 하루 20개, 30분, 주 3회)
                - 체계적인 학습과 실행 계획 필요
            """.trimIndent()
            4 -> """
                난이도 4 (직장인/아마추어 수준): 상당한 시간과 노력이 필요한 미션
                - 많은 시간 투자 (1-2시간)
                - 높은 목표 수치 (예: 하루 50개, 1시간, 주 5회)
                - 전문성을 향한 도전
            """.trimIndent()
            5 -> """
                난이도 5 (전문가 수준): 고급 수준의 미션
                - 집중적 시간 투자 (2시간 이상)
                - 전문가 수준 목표 수치 (예: 하루 100개, 2시간, 매일)
                - 높은 수준의 전문성과 헌신 필요
            """.trimIndent()
            else -> getDifficultyDescription(3)
        }
    }

    /**
     * Clova API 응답 파싱
     */
    private fun parseMissionResponse(response: String): List<Mission> {
        return try {
            val jsonResponse = objectMapper.readValue<MissionResponse>(response.trim())
            jsonResponse.missions
        } catch (e: Exception) {
            logger.error("미션 응답 파싱 실패: ${e.message}")
            emptyList()
        }
    }

    /**
     * Clova API 응답 형식
     */
    private data class MissionResponse(
        val missions: List<Mission>
    )

    /**
     * 미션 (내부 사용)
     */
    private data class Mission(
        val content: String,
        val relatedInterest: String
    )
}
