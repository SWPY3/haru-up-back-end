package com.haruUp.ranking.service

import com.haruUp.global.clova.ClovaApiClient
import com.haruUp.interest.repository.InterestEmbeddingJpaRepository
import com.haruUp.interest.repository.MemberInterestJpaRepository
import com.haruUp.member.infrastructure.MemberProfileRepository
import com.haruUp.mission.infrastructure.MemberMissionRepository
import com.haruUp.missionembedding.repository.MissionEmbeddingRepository
import com.haruUp.ranking.domain.AgeGroup
import com.haruUp.ranking.domain.RankingMissionDailyEntity
import com.haruUp.ranking.dto.RankingBatchResult
import com.haruUp.ranking.repository.RankingMissionDailyRepository
import org.slf4j.LoggerFactory
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Period

@Service
class RankingBatchService(
    private val memberMissionRepository: MemberMissionRepository,
    private val missionEmbeddingRepository: MissionEmbeddingRepository,
    private val memberInterestRepository: MemberInterestJpaRepository,
    private val interestEmbeddingRepository: InterestEmbeddingJpaRepository,
    private val memberProfileRepository: MemberProfileRepository,
    private val rankingMissionDailyRepository: RankingMissionDailyRepository,
    private val clovaApiClient: ClovaApiClient
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        // 임베딩 유사도 threshold (코사인 거리, 낮을수록 유사)
        const val SIMILARITY_THRESHOLD = 0.3
    }

    /**
     * 랭킹 데이터 수집 배치 실행
     * - 오늘 선택된 미션 중 아직 ranking에 없는 것들 수집
     * - 라벨이 없으면 생성
     */
    @Transactional
    fun executeBatch(targetDate: LocalDate = LocalDate.now()): RankingBatchResult {
        logger.info("랭킹 배치 시작 - targetDate: $targetDate")

        var processedCount = 0
        var newLabelCount = 0
        var existingLabelCount = 0
        var skippedCount = 0
        val errors = mutableListOf<String>()

        // 1. 대상 조회: is_selected = true, target_date = 오늘
        val selectedMissions = memberMissionRepository.findSelectedMissionsByTargetDate(targetDate)
        logger.info("선택된 미션 수: ${selectedMissions.size}")

        // 2. 이미 ranking에 있는 member_mission_id 제외
        val existingMemberMissionIds = if (selectedMissions.isNotEmpty()) {
            rankingMissionDailyRepository.findAllByMemberMissionIdIn(
                selectedMissions.mapNotNull { it.id }
            ).map { it.memberMissionId }.toSet()
        } else {
            emptySet()
        }

        val targetMissions = selectedMissions.filter { it.id !in existingMemberMissionIds }
        logger.info("처리 대상 미션 수: ${targetMissions.size} (이미 처리됨: ${existingMemberMissionIds.size})")

        // 3. 각 미션 처리
        for (memberMission in targetMissions) {
            try {
                val memberMissionId = memberMission.id ?: continue
                val missionId = memberMission.missionId

                // mission_embedding 조회
                val missionEmbedding = missionEmbeddingRepository.findByIdOrNull(missionId)
                if (missionEmbedding == null) {
                    logger.warn("미션을 찾을 수 없음: missionId=$missionId")
                    skippedCount++
                    continue
                }

                // member_interest -> interest_embedding 조회
                val memberInterest = memberInterestRepository.findByIdOrNull(memberMission.memberInterestId)
                val interestEmbedding = memberInterest?.let {
                    interestEmbeddingRepository.findByIdOrNull(it.interestId)
                }

                // member_profile 조회
                val memberProfile = memberProfileRepository.findByMemberId(memberMission.memberId)

                // 라벨 처리
                val labelName = processLabel(
                    missionId = missionId,
                    existingLabel = missionEmbedding.labelName,
                    missionContent = missionEmbedding.missionContent,
                    embedding = missionEmbedding.embedding,
                    interestPath = interestEmbedding?.fullPath ?: memberInterest?.directFullPath
                )

                if (missionEmbedding.labelName == null && labelName != null) {
                    newLabelCount++
                } else if (missionEmbedding.labelName != null) {
                    existingLabelCount++
                }

                // 연령대 계산
                val ageGroup = memberProfile?.birthDt?.let { birthDt ->
                    val age = Period.between(birthDt.toLocalDate(), LocalDate.now()).years
                    AgeGroup.fromAge(age)
                }

                // ranking_mission_daily INSERT
                val rankingEntity = RankingMissionDailyEntity(
                    rankingDate = targetDate,
                    memberMissionId = memberMissionId,
                    missionId = missionId,
                    labelName = labelName,
                    interestFullPath = interestEmbedding?.fullPath ?: memberInterest?.directFullPath,
                    gender = memberProfile?.gender,
                    ageGroup = ageGroup,
                    jobId = memberProfile?.jobId,
                    jobDetailId = memberProfile?.jobDetailId
                )

                rankingMissionDailyRepository.save(rankingEntity)
                processedCount++

                logger.debug("처리 완료: memberMissionId=$memberMissionId, labelName=$labelName")

            } catch (e: Exception) {
                val errorMsg = "처리 실패: memberMissionId=${memberMission.id}, error=${e.message}"
                logger.error(errorMsg, e)
                errors.add(errorMsg)
            }
        }

        val result = RankingBatchResult(
            processedCount = processedCount,
            newLabelCount = newLabelCount,
            existingLabelCount = existingLabelCount,
            skippedCount = skippedCount,
            errors = errors
        )

        logger.info("랭킹 배치 완료 - $result")
        return result
    }

    /**
     * 라벨 처리
     * 1. 기존 라벨이 있으면 그대로 사용
     * 2. 없으면 임베딩 유사도로 기존 라벨 검색
     * 3. 유사한 라벨이 없으면 LLM으로 새 라벨 생성
     */
    private fun processLabel(
        missionId: Long,
        existingLabel: String?,
        missionContent: String,
        embedding: String?,
        interestPath: List<String>?
    ): String? {
        // 1. 기존 라벨이 있으면 그대로 사용
        if (!existingLabel.isNullOrBlank()) {
            return existingLabel
        }

        // 2. 임베딩이 있으면 유사도로 기존 라벨 검색
        if (!embedding.isNullOrBlank()) {
            val similarMission = missionEmbeddingRepository.findSimilarMissionWithLabel(
                embedding = embedding,
                threshold = SIMILARITY_THRESHOLD
            )

            if (similarMission?.labelName != null) {
                // 유사한 미션의 라벨을 재사용하고 현재 미션에도 저장
                val reusedLabel = similarMission.labelName
                missionEmbeddingRepository.updateLabelName(
                    id = missionId,
                    labelName = reusedLabel!!,
                    updatedAt = LocalDateTime.now()
                )
                logger.info("기존 라벨 재사용: missionId=$missionId, label=$reusedLabel")
                return reusedLabel
            }
        }

        // 3. LLM으로 새 라벨 생성
        return try {
            val newLabel = generateLabelWithLLM(missionContent, interestPath)
            if (!newLabel.isNullOrBlank()) {
                missionEmbeddingRepository.updateLabelName(
                    id = missionId,
                    labelName = newLabel,
                    updatedAt = LocalDateTime.now()
                )
                logger.info("새 라벨 생성: missionId=$missionId, label=$newLabel")
            }
            newLabel
        } catch (e: Exception) {
            logger.error("라벨 생성 실패: missionId=$missionId, error=${e.message}", e)
            null
        }
    }

    /**
     * LLM을 사용하여 미션 라벨 생성
     */
    private fun generateLabelWithLLM(missionContent: String, interestPath: List<String>?): String? {
        val prompt = buildLabelPrompt(missionContent, interestPath)

        val response = clovaApiClient.generateText(
            userMessage = prompt,
            systemMessage = """
                당신은 미션 내용을 분석하여 대표 라벨(그룹명)을 생성하는 전문가입니다.
                규칙을 엄격히 준수하여 간결한 라벨만 출력하세요.
            """.trimIndent(),
            temperature = 0.3
        )

        // 응답에서 라벨 추출 (따옴표, 공백 제거)
        return response.trim()
            .replace("\"", "")
            .replace("'", "")
            .take(100) // 최대 100자
            .ifBlank { null }
    }

    private fun buildLabelPrompt(missionContent: String, interestPath: List<String>?): String {
        return """
            미션 내용을 분석하여 대표 라벨(그룹명)을 생성해주세요.

            [규칙]
            - 구체적인 숫자, 시간, 횟수는 제외하고 핵심 행동만 추출
            - 10자 이내의 간결한 명사형으로 작성
            - 따옴표, 설명 없이 라벨명만 출력

            [예시]
            - "영어 단어 20개 외우기" → 영어 단어 외우기
            - "30분 조깅하기" → 조깅하기
            - "물 2L 마시기" → 물 마시기
            - "책 30페이지 읽기" → 독서하기
            - "플랭크 1분 하기" → 플랭크하기

            [관심사 경로]
            ${interestPath?.joinToString(" > ") ?: "없음"}

            [미션 내용]
            $missionContent

            라벨:
        """.trimIndent()
    }
}
