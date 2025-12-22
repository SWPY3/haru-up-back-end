package com.haruUp.missionembedding.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.haruUp.interest.model.InterestPath
import com.haruUp.interest.model.UserInterests
import com.haruUp.missionembedding.dto.MissionDto
import com.haruUp.missionembedding.dto.MissionGroupDto
import com.haruUp.global.clova.ClovaApiClient
import com.haruUp.global.clova.ImprovedMissionRecommendationPrompt
import com.haruUp.global.clova.MissionMemberProfile
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
     * 관심사별로 미션 5개씩 추천 (그룹화된 형태로 반환)
     *
     * 각 관심사에 대해 난이도 1~5 각각 1개씩 미션을 추천
     * - RAG: 난이도별로 검색 (DB 쿼리)
     * - AI: 부족한 난이도만 모아서 LLM 1회 호출
     *
     * @param interests 사용자가 선택한 관심사 목록 (seqNo, InterestPath)
     * @param memberProfile 멤버 프로필
     * @return 관심사별 그룹화된 미션 목록 (각 관심사당 5개, 난이도 1~5)
     */
    suspend fun recommendMissions(
        interests: List<Pair<Int?, InterestPath>>,  // (seqNo, InterestPath)
        memberProfile: MissionMemberProfile
    ): List<MissionGroupDto> {
        logger.info("미션 추천 시작 - 관심사 개수: ${interests.size}")

        val missionGroups = mutableListOf<MissionGroupDto>()

        // 각 관심사에 대해 난이도 1~5 미션 추천
        for ((memberInterestId, interestPath) in interests) {
            logger.info("처리 중: seqNo=$memberInterestId, path=${interestPath.toPathString()}")

            try {
                // RAG + AI 하이브리드로 난이도 1~5 미션 생성
                val missions = recommendMissionsForSingleInterest(
                    interestPath = interestPath,
                    memberProfile = memberProfile
                )

                // MissionDto 리스트 생성
                val missionDtos = missions.map { mission ->
                    MissionDto(
                        member_mission_id = null,
                        mission_id = mission.id,
                        content = mission.content,
                        relatedInterest = mission.relatedInterest,
                        difficulty = mission.difficulty,
                        createdType = mission.createdType
                    )
                }

                // seqNo별 그룹으로 묶기
                missionGroups.add(
                    MissionGroupDto(
                        memberInterestId = memberInterestId,
                        data = missionDtos
                    )
                )
                logger.info("seqNo=$memberInterestId 미션 추천 완료: ${missionDtos.size}개 (난이도 1~5)")

            } catch (e: Exception) {
                logger.error("seqNo=$memberInterestId 미션 추천 실패: ${e.message}", e)
                // 실패한 경우 빈 그룹 추가
                missionGroups.add(
                    MissionGroupDto(
                        memberInterestId = memberInterestId,
                        data = emptyList()
                    )
                )
            }
        }

        logger.info("전체 미션 추천 완료: ${missionGroups.sumOf { it.data.size }}개")
        return missionGroups
    }

    /**
     * 오늘의 미션 추천 (단일 관심사 기반)
     *
     * 사용자 프로필(직업, 직업상세, 성별, 나이)과 단일 관심사를 기반으로 미션 추천
     * 기존 recommendMissions와 분리하여 향후 로직 변경에 유연하게 대응
     *
     * @param interestPath 관심사 경로
     * @param memberProfile 멤버 프로필 (직업, 직업상세, 성별, 나이 포함)
     * @param difficulties 추천할 난이도 목록 (1~5), null이면 전체 난이도 추천
     * @param excludeIds 제외할 미션 ID 목록
     * @return 미션 목록 (MissionDto 리스트)
     */
    suspend fun recommendTodayMissions(
        interestPath: InterestPath,
        memberProfile: MissionMemberProfile,
        difficulties: List<Int>? = null,
        excludeIds: List<Long> = emptyList()
    ): List<MissionDto> {
        val targetDifficulties = difficulties ?: listOf(1, 2, 3, 4, 5)
        logger.info("오늘의 미션 추천 시작 - 관심사: ${interestPath.toPathString()}, 난이도: $targetDifficulties, 제외 ID 개수: ${excludeIds.size}")

        try {
            val missions = recommendTodayMissionsInternal(
                interestPath = interestPath,
                memberProfile = memberProfile,
                difficulties = targetDifficulties,
                excludeIds = excludeIds
            )

            val missionDtos = missions.map { mission ->
                MissionDto(
                    member_mission_id = null,
                    mission_id = mission.id,
                    content = mission.content,
                    relatedInterest = mission.relatedInterest,
                    difficulty = mission.difficulty,
                    createdType = mission.createdType
                )
            }

            logger.info("오늘의 미션 추천 완료: ${missionDtos.size}개")
            return missionDtos

        } catch (e: Exception) {
            logger.error("오늘의 미션 추천 실패: ${e.message}", e)
            return emptyList()
        }
    }

    /**
     * 오늘의 미션 추천 내부 로직 (LLM 전용)
     *
     * RAG 사용하지 않고 매번 LLM을 호출하여 미션 생성
     */
    private suspend fun recommendTodayMissionsInternal(
        interestPath: InterestPath,
        memberProfile: MissionMemberProfile,
        difficulties: List<Int>,
        excludeIds: List<Long> = emptyList()
    ): List<Mission> {
        val interestPathString = interestPath.toPathString()

        logger.info("오늘의 미션 LLM 생성 시작: $interestPathString, difficulties=$difficulties, excludeIds=${excludeIds.size}개")

        // LLM으로 미션 생성
        val aiMissions = generateTodayMissionsWithAI(
            interestPath = interestPath,
            memberProfile = memberProfile,
            difficulties = difficulties,
            excludeIds = excludeIds
        )

        // AI로 생성한 미션을 DB에 저장 (embedding 없이) 후 id 포함하여 리스트에 추가
        // relatedInterest는 LLM 응답이 아닌 실제 interestPath 사용
        val actualInterestPath = interestPath.toPathList()
        val missionsWithId = aiMissions.mapNotNull { mission ->
            val missionDifficulty = mission.difficulty
            try {
                val savedEntity = missionEmbeddingService.saveMissionWithoutEmbedding(
                    directFullPath = actualInterestPath,
                    difficulty = missionDifficulty,
                    missionContent = mission.content
                )
                Mission(
                    id = savedEntity?.id,
                    content = mission.content,
                    relatedInterest = actualInterestPath,
                    difficulty = savedEntity?.difficulty ?: missionDifficulty
                )
            } catch (e: Exception) {
                logger.warn("오늘의 미션 저장 실패: ${mission.content}, 에러: ${e.message}")
                Mission(
                    id = null,
                    content = mission.content,
                    relatedInterest = actualInterestPath,
                    difficulty = missionDifficulty
                )
            }
        }

        logger.info("오늘의 미션 LLM 생성 완료: ${missionsWithId.size}개")
        return missionsWithId.take(difficulties.size)
    }

    /**
     * 오늘의 미션용 AI 미션 생성
     *
     * LLM을 호출하여 미션 생성, excludeIds가 있으면 해당 미션 제외
     * @param difficulties 추천할 난이도 목록 (예: [1, 2, 3] 또는 [2, 4, 5])
     */
    private suspend fun generateTodayMissionsWithAI(
        interestPath: InterestPath,
        memberProfile: MissionMemberProfile,
        difficulties: List<Int>,
        excludeIds: List<Long> = emptyList()
    ): List<Mission> {
        val userInterests = UserInterests(listOf(interestPath))

        val basePrompt = ImprovedMissionRecommendationPrompt.createUserMessageForAllInterests(
            userInterests = userInterests,
            missionMemberProfile = memberProfile
        )

        // 제외할 미션 내용 조회
        logger.info("제외할 미션 ID 목록: $excludeIds")
        val excludeMissionsText = if (excludeIds.isNotEmpty()) {
            val excludeMissions = missionEmbeddingService.findByIds(excludeIds)
            logger.info("제외할 미션 조회 결과: ${excludeMissions.size}개")
            if (excludeMissions.isNotEmpty()) {
                val missionList = excludeMissions.mapIndexed { _, entity ->
                    "- ${entity.missionContent}"
                }.joinToString("\n")
                logger.info("제외할 미션 내용:\n$missionList")
                """

###############################################
# 중요: 아래 미션들은 절대 추천하지 마세요 #
###############################################

<EXCLUDED_MISSIONS>
$missionList
</EXCLUDED_MISSIONS>

위 목록에 있는 미션과 동일하거나 의미가 유사한 미션은 반드시 제외해야 합니다.

제외 판단 기준:
1. 동일한 표현: 글자가 완전히 같은 경우
2. 유사한 의미: 같은 활동을 다른 말로 표현한 경우
   - 예: "스트레칭하기" ≈ "몸 풀기" ≈ "스트레칭으로 몸 풀기"
   - 예: "유산소 운동하기" ≈ "유산소 운동 해보기" ≈ "가벼운 유산소"
3. 포함 관계: 한 미션이 다른 미션을 포함하는 경우
   - 예: "운동하기"는 "헬스장에서 운동하기"를 포함

"""
            } else ""
        } else ""

        // 난이도별 JSON 응답 형식 생성
        val difficultyJsonExamples = difficulties.joinToString(",\n    ") { diff ->
            """{"content": "미션내용", "relatedInterest": ["대분류", "중분류", "소분류"], "difficulty": $diff}"""
        }

        val difficultyListStr = difficulties.joinToString(", ")
        val userMessage = """
$basePrompt

===== 생성 요청 =====
난이도 $difficultyListStr 각각 1개씩, 총 ${difficulties.size}개의 새로운 미션을 생성하세요.
$excludeMissionsText
===== 난이도 기준 =====
- 난이도 1 (매우 쉬움): 5-10분 소요, 누구나 쉽게 할 수 있는 작은 목표
- 난이도 2 (쉬움): 15-20분 소요, 약간의 노력이 필요한 목표
- 난이도 3 (보통): 30분-1시간 소요, 체계적인 실행이 필요한 목표
- 난이도 4 (어려움): 1-2시간 소요, 상당한 노력이 필요한 목표
- 난이도 5 (매우 어려움): 2시간 이상 소요, 높은 집중력과 전문성이 필요한 목표

===== 미션 형식 요구사항 =====
- 정량적 수치(횟수, 시간, 개수)를 포함하지 마세요
- 일반적이고 자유로운 형태로 작성하세요

올바른 예시:
- "영어 단어 공부하기" (O)
- "헬스장에서 운동하기" (O)

잘못된 예시:
- "하루 10개 영단어 암기하기" (X - 수치 포함)
- "주 3회 30분 운동하기" (X - 수치 포함)

===== 응답 전 체크리스트 =====
응답하기 전에 다음을 반드시 확인하세요:
[ ] 각 미션이 <EXCLUDED_MISSIONS> 목록과 중복되지 않는가?
[ ] 각 미션이 목록의 미션과 유사한 의미를 가지지 않는가?
[ ] ${difficulties.size}개 미션이 모두 서로 다른 새로운 활동인가?
[ ] 요청된 난이도($difficultyListStr)만 생성했는가?

===== 응답 형식 (JSON) =====
```json
{
  "missions": [
    $difficultyJsonExamples
  ]
}
```
        """.trim()

        logger.debug("오늘의 미션 Clova API 호출: $userMessage")

        val response = clovaApiClient.generateText(
            userMessage = userMessage,
            systemMessage = ImprovedMissionRecommendationPrompt.SYSTEM_PROMPT,
            temperature = 0.8  // 다양성 증가를 위해 temperature 높임, seed는 자동 랜덤
        )

        logger.debug("오늘의 미션 Clova API 응답: $response")

        return parseMissionResponse(response).take(difficulties.size)
    }

    /**
     * 단일 관심사에 대해 난이도 1~5 미션 5개 추천
     *
     * - RAG에서 5개 모두 조회되면 RAG 결과 반환
     * - 5개 미만이면 RAG 무시하고 LLM으로 5개 생성
     */
    private suspend fun recommendMissionsForSingleInterest(
        interestPath: InterestPath,
        memberProfile: MissionMemberProfile
    ): List<Mission> {
        // 1. RAG: 난이도 1~5 각각 1개씩 검색 (1회 쿼리, API 호출 없음)
        val ragMissions = try {
            missionEmbeddingService.findOnePerDifficulty(interestPath.toPathList())
        } catch (e: Exception) {
            logger.warn("RAG 검색 실패: ${e.message}")
            emptyList()
        }

        logger.info("RAG DATA: ${ragMissions}")

        // 2. RAG 5개 모두 조회되면 반환
        if (ragMissions.size == 5) {
            logger.info("RAG로 5개 미션 조회 완료: ${interestPath.toPathString()}")
            return ragMissions.map { entity ->
                Mission(
                    id = entity.id,
                    content = entity.missionContent,
                    relatedInterest = entity.directFullPath,
                    difficulty = entity.difficulty,
                    createdType = "EMBEDDING"
                )
            }.sortedBy { it.difficulty }
        }

        // 3. 5개 미만이면 LLM으로 전체 생성
        logger.info("RAG ${ragMissions.size}개 조회, LLM으로 5개 생성: ${interestPath.toPathString()}")
        val aiMissions = generateMissionsAllDifficulties(interestPath, memberProfile)

        // AI 미션 DB 저장 후 반환
        return aiMissions.mapNotNull { mission ->
            try {
                val saved = missionEmbeddingService.saveMissionWithoutEmbedding(
                    directFullPath = interestPath.toPathList(),
                    difficulty = mission.difficulty,
                    missionContent = mission.content
                )
                mission.copy(id = saved?.id)
            } catch (e: Exception) {
                logger.warn("미션 저장 실패: ${mission.content}")
                mission
            }
        }.sortedBy { it.difficulty }
    }

    /**
     * LLM으로 난이도 1~5 미션 5개 생성
     */
    private suspend fun generateMissionsAllDifficulties(
        interestPath: InterestPath,
        memberProfile: MissionMemberProfile
    ): List<Mission> {
        val userInterests = UserInterests(listOf(interestPath))
        val basePrompt = ImprovedMissionRecommendationPrompt.createUserMessageForAllInterests(
            userInterests = userInterests,
            missionMemberProfile = memberProfile
        )

        val userMessage = """
$basePrompt

**생성할 미션: 난이도 1, 2, 3, 4, 5 각각 1개씩, 총 5개**

각 난이도별 기준:
- 난이도 1 (중학생 수준): 5-10분 소요, 작은 목표
- 난이도 2 (고등학생 수준): 15-20분 소요, 중간 목표
- 난이도 3 (대학생 수준): 30분-1시간 소요, 상당한 목표
- 난이도 4 (직장인 수준): 1-2시간 소요, 높은 목표
- 난이도 5 (전문가 수준): 2시간 이상 소요, 전문가 목표

**중요:** 반드시 검증 가능한 정량적 수치를 포함해주세요.

**응답 형식 (JSON):**
```json
{
  "missions": [
    {"content": "미션내용", "relatedInterest": ["대분류", "중분류", "소분류"], "difficulty": 난이도숫자}
  ]
}
```
        """.trim()

        val response = clovaApiClient.generateText(
            userMessage = userMessage,
            systemMessage = ImprovedMissionRecommendationPrompt.SYSTEM_PROMPT,
            temperature = 0.8  // 다양성 증가를 위해 temperature 높임, seed는 자동 랜덤
        )

        return parseMissionResponseWithDifficulty(response)
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
     * Clova API 응답 파싱 (difficulty 없는 버전)
     */
    private fun parseMissionResponse(response: String): List<Mission> {
        return try {
            // JSON 블록 추출 (```json ... ``` 형식 처리)
            val jsonContent = extractJsonFromResponse(response)
            val jsonResponse = objectMapper.readValue<MissionResponse>(jsonContent)
            jsonResponse.missions
        } catch (e: Exception) {
            logger.error("미션 응답 파싱 실패: ${e.message}")
            logger.debug("파싱 실패한 응답: $response")
            emptyList()
        }
    }

    /**
     * Clova API 응답 파싱 (difficulty 포함 버전)
     */
    private fun parseMissionResponseWithDifficulty(response: String): List<Mission> {
        return try {
            // JSON 블록 추출 (```json ... ``` 형식 처리)
            val jsonContent = extractJsonFromResponse(response)
            val jsonResponse = objectMapper.readValue<MissionResponse>(jsonContent)
            jsonResponse.missions
        } catch (e: Exception) {
            logger.error("미션 응답 파싱 실패 (difficulty 포함): ${e.message}")
            emptyList()
        }
    }

    /**
     * 응답에서 JSON 부분 추출
     */
    private fun extractJsonFromResponse(response: String): String {
        val trimmed = response.trim()

        // ```json ... ``` 형식 처리
        val jsonBlockRegex = Regex("```json\\s*(.+?)\\s*```", RegexOption.DOT_MATCHES_ALL)
        val jsonBlockMatch = jsonBlockRegex.find(trimmed)
        if (jsonBlockMatch != null) {
            return jsonBlockMatch.groupValues[1].trim()
        }

        // ``` ... ``` 형식 처리
        val codeBlockRegex = Regex("```\\s*(.+?)\\s*```", RegexOption.DOT_MATCHES_ALL)
        val codeBlockMatch = codeBlockRegex.find(trimmed)
        if (codeBlockMatch != null) {
            return codeBlockMatch.groupValues[1].trim()
        }

        // 이미 JSON 형식이면 그대로 반환
        return trimmed
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
        val id: Long? = null,
        val content: String,
        val relatedInterest: List<String>,
        val difficulty: Int? = null,
        val createdType: String = "AI"  // "EMBEDDING" or "AI"
    )
}
