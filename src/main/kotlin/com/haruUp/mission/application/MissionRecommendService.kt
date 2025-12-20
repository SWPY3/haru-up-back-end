package com.haruUp.mission.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.haruUp.category.repository.JobDetailRepository
import com.haruUp.category.repository.JobRepository
import com.haruUp.global.clova.MissionMemberProfile
import com.haruUp.interest.model.InterestPath
import com.haruUp.interest.repository.MemberInterestJpaRepository
import com.haruUp.member.infrastructure.MemberProfileRepository
import com.haruUp.mission.domain.MemberMission
import com.haruUp.mission.domain.MissionCandidateDto
import com.haruUp.mission.domain.MissionRecommendResult
import com.haruUp.mission.domain.MissionStatus
import com.haruUp.mission.infrastructure.MemberMissionRepository
import com.haruUp.mission.infrastructure.MissionAiClient
import com.haruUp.missionembedding.dto.MissionRecommendationResponse
import com.haruUp.missionembedding.repository.MissionEmbeddingRepository
import com.haruUp.missionembedding.service.MissionRecommendationService
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Period

@Service
class MissionRecommendService(
    private val redisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper,
    private val missionEmbeddingRepository: MissionEmbeddingRepository,
    private val missionAiClient: MissionAiClient,
    private val missionRecommendationService: MissionRecommendationService,
    private val memberProfileRepository: MemberProfileRepository,
    private val memberMissionRepository: MemberMissionRepository,
    private val jobRepository: JobRepository,
    private val jobDetailRepository: JobDetailRepository,
    private val memberInterestRepository: MemberInterestJpaRepository
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val TODAY_RETRY_TTL = Duration.ofHours(24)  // 24시간 후 자동 만료

    /**
     * 오늘의 미션 추천 조회
     *
     * member_mission에서 조건에 맞는 미션 조회:
     * - deleted = false
     * - targetDate = 오늘
     * - missionStatus IN (READY, ACTIVE)
     */
    fun recommend(memberId: Long, memberInterestId: Long): MissionRecommendResult {
        val today = LocalDate.now()

        // member_mission에서 오늘의 미션 조회
        val memberMissions = memberMissionRepository.findTodayMissions(
            memberId = memberId,
            memberInterestId = memberInterestId,
            targetDate = today,
            statuses = listOf(MissionStatus.READY, MissionStatus.ACTIVE)
        )

        logger.info("오늘의 미션 조회 - memberId: $memberId, memberInterestId: $memberInterestId, 결과: ${memberMissions.size}개")

        // mission_embeddings에서 상세 정보 조회하여 MissionCandidateDto로 변환
        val missions = memberMissions.mapNotNull { memberMission ->
            missionEmbeddingRepository.findById(memberMission.missionId).orElse(null)?.let { embedding ->
                MissionCandidateDto(
                    memberMissionId = memberMission.id!!,
                    missionStatus = memberMission.missionStatus,
                    content = embedding.missionContent,
                    directFullPath = embedding.directFullPath,
                    difficulty = embedding.difficulty,
                    targetDate = memberMission.targetDate,
                    reason = "오늘의 미션"
                )
            }
        }

        return MissionRecommendResult(
            missions = missions
        )
    }

    /**
     * 재추천 (memberInterestId 기반)
     *
     * 사용자 프로필과 관심사 정보를 기반으로 미션 재추천
     * - ACTIVE 상태인 기존 미션 자동 제외
     * - Redis에 저장된 이전 추천 미션 자동 제외
     * - 추천된 미션 ID는 Redis에 캐싱 (24시간)
     * - reset_mission_count 카운트 증가
     */
    suspend fun retryWithInterest(memberId: Long, memberInterestId: Long): MissionRecommendationResponse {
        logger.info("오늘의 미션 재추천 요청 - 사용자: $memberId, memberInterestId: $memberInterestId")

        // 1. DB에서 사용자 프로필 조회
        val memberProfileEntity = memberProfileRepository.findByMemberId(memberId)
            ?: throw IllegalArgumentException("사용자 프로필을 찾을 수 없습니다.")

        // 2. 직업 정보 조회
        val jobName = memberProfileEntity.jobId?.let { jobId ->
            jobRepository.findById(jobId).orElse(null)?.jobName
        }
        val jobDetailName = memberProfileEntity.jobDetailId?.let { jobDetailId ->
            jobDetailRepository.findById(jobDetailId).orElse(null)?.jobDetailName
        }

        val missionMemberProfile = MissionMemberProfile(
            age = memberProfileEntity.birthDt?.let { calculateAge(it) },
            gender = memberProfileEntity.gender?.name,
            jobName = jobName,
            jobDetailName = jobDetailName
        )

        // 3. 멤버 관심사 조회
        val memberInterest = memberInterestRepository.findById(memberInterestId).orElse(null)
            ?: throw IllegalArgumentException("멤버 관심사를 찾을 수 없습니다. (memberInterestId: $memberInterestId)")

        // 3-1. 해당 관심사가 현재 사용자의 것인지 확인
        if (memberInterest.memberId != memberId) {
            throw IllegalArgumentException("해당 관심사에 접근 권한이 없습니다.")
        }

        val interestPath = memberInterest.directFullPath?.let { path ->
            InterestPath(
                mainCategory = path.getOrNull(0) ?: "",
                middleCategory = path.getOrNull(1),
                subCategory = path.getOrNull(2)
            )
        } ?: throw IllegalArgumentException("관심사 경로 정보가 없습니다. (memberInterestId: $memberInterestId)")

        logger.info("관심사 경로: ${interestPath.toPathString()}")

        // 4. 제외할 미션 ID 수집
        val activeMissionIds = memberMissionRepository.findMissionIdsByMemberIdAndStatus(
            memberId = memberId,
            status = MissionStatus.ACTIVE
        )
        logger.info("ACTIVE 상태 미션 ID: $activeMissionIds")

        val cachedMissionIds = getRecommendedMissionIds(
            memberId = memberId,
            interestId = memberInterest.interestId
        )
        logger.info("Redis 캐시 미션 ID: $cachedMissionIds")

        val excludeIds = (activeMissionIds + cachedMissionIds).distinct()
        logger.info("제외할 미션 ID 총합: ${excludeIds.size}개")

        // 5. 미션 추천 (난이도 1~5 각각 1개씩)
        val missionDtos = missionRecommendationService.recommendTodayMissions(
            interestPath = interestPath,
            memberProfile = missionMemberProfile,
            difficulty = null,
            excludeIds = excludeIds
        )

        // 5-1. 해당 관심사의 기존 READY 상태 member_mission soft delete
        val deletedCount = memberMissionRepository.softDeleteByMemberIdAndInterestIdAndStatus(
            memberId = memberId,
            memberInterestId = memberInterestId,
            status = MissionStatus.READY,
            deletedAt = LocalDateTime.now()
        )
        logger.info("기존 READY 상태 미션 soft delete 완료 (memberInterestId: $memberInterestId): ${deletedCount}개")

        // 5-2. 추천된 미션들을 member_mission에 READY 상태로 저장
        val savedMemberMissions = missionDtos.mapNotNull { missionDto ->
            missionDto.id?.let { missionId ->
                try {
                    val memberMission = MemberMission(
                        memberId = memberId,
                        missionId = missionId,
                        memberInterestId = memberInterestId,
                        missionStatus = MissionStatus.READY,
                        expEarned = 0
                    )
                    memberMissionRepository.save(memberMission)
                } catch (e: Exception) {
                    logger.error("member_mission 저장 실패: missionId=$missionId, 에러: ${e.message}")
                    null
                }
            }
        }
        logger.info("member_mission READY 상태로 저장 완료: ${savedMemberMissions.size}개")

        // 6. 추천된 미션 ID를 Redis에 저장
        val recommendedMissionIds = missionDtos.mapNotNull { it.id }
        if (recommendedMissionIds.isNotEmpty()) {
            saveRecommendedMissionIds(
                memberId = memberId,
                interestId = memberInterest.interestId,
                missionIds = recommendedMissionIds
            )
        }

        // 7. reset_mission_count 증가
        memberInterest.incrementResetMissionCount()
        memberInterestRepository.save(memberInterest)
        logger.info("reset_mission_count 증가: ${memberInterest.resetMissionCount}")

        // 8. 응답 생성
        val missionGroup = com.haruUp.missionembedding.dto.MissionGroupDto(
            memberInterestId = 1,
            data = missionDtos
        )

        logger.info("오늘의 미션 재추천 성공: ${missionDtos.size}개")

        return MissionRecommendationResponse(
            missions = listOf(missionGroup),
            totalCount = missionDtos.size
        )
    }

    /**
     * 생년월일로부터 나이 계산
     */
    private fun calculateAge(birthDt: LocalDateTime): Int {
        val birthDate = birthDt.toLocalDate()
        val now = LocalDateTime.now().toLocalDate()
        return Period.between(birthDate, now).years
    }

    /**
     * Redis 캐시
     */
    private fun cache(key: String, value: MissionRecommendResult) {
        redisTemplate.opsForValue().set(
            key,
            objectMapper.writeValueAsString(value),
            Duration.ofSeconds(secondsUntilMidnight())
        )
    }

    private fun secondsUntilMidnight(): Long {
        val now = LocalDateTime.now()
        val midnight = now.toLocalDate().plusDays(1).atStartOfDay()
        return Duration.between(now, midnight).seconds
    }

    /**
     * 추천된 미션 ID 목록 저장
     *
     * @param memberId 사용자 ID
     * @param interestId 관심사 ID
     * @param missionIds 추천된 미션 ID 목록
     */
    fun saveRecommendedMissionIds(memberId: Long, interestId: Long, missionIds: List<Long>) {
        val key = MissionRecommendRedisKey.retry(memberId, interestId, LocalDate.now())
        try {
            // 기존 값에 추가
            val existingIds = getRecommendedMissionIds(memberId, interestId)
            val allIds = (existingIds + missionIds).distinct()

            // Set으로 저장
            redisTemplate.delete(key)
            if (allIds.isNotEmpty()) {
                redisTemplate.opsForSet().add(key, *allIds.map { it.toString() }.toTypedArray())
                redisTemplate.expire(key, TODAY_RETRY_TTL)
            }

            logger.info("추천 미션 ID 캐시 저장 - key: $key, ids: $allIds")
        } catch (e: Exception) {
            logger.error("추천 미션 ID 캐시 저장 실패 - key: $key, error: ${e.message}")
        }
    }

    /**
     * 추천된 미션 ID 목록 조회
     *
     * @param memberId 사용자 ID
     * @param interestId 관심사 ID
     * @return 이전에 추천된 미션 ID 목록
     */
    fun getRecommendedMissionIds(memberId: Long, interestId: Long): List<Long> {
        val key = MissionRecommendRedisKey.retry(memberId, interestId, LocalDate.now())
        return try {
            val members = redisTemplate.opsForSet().members(key)
            val ids = members?.mapNotNull { it.toString().toLongOrNull() } ?: emptyList()
            logger.info("추천 미션 ID 캐시 조회 - key: $key, ids: $ids")
            ids
        } catch (e: Exception) {
            logger.error("추천 미션 ID 캐시 조회 실패 - key: $key, error: ${e.message}")
            emptyList()
        }
    }
}


object MissionRecommendRedisKey {
    fun retry(memberId: Long, memberInterestId: Long, date: LocalDate) =
        "today-mission:$memberId:$memberInterestId:$date"
}