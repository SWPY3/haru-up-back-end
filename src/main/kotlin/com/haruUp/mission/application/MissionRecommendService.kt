package com.haruUp.mission.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.haruUp.category.repository.JobDetailRepository
import com.haruUp.category.repository.JobRepository
import com.haruUp.global.clova.MissionMemberProfile
import com.haruUp.interest.model.InterestPath
import com.haruUp.interest.repository.MemberInterestJpaRepository
import com.haruUp.member.infrastructure.MemberProfileRepository
import com.haruUp.mission.domain.MissionCandidateDto
import com.haruUp.mission.domain.MissionRecommendResult
import com.haruUp.mission.domain.MissionStatus
import com.haruUp.mission.infrastructure.MemberMissionRepository
import com.haruUp.mission.infrastructure.MissionAiClient
import com.haruUp.missionembedding.dto.MissionRecommendationResponse
import com.haruUp.missionembedding.repository.MissionEmbeddingRepository
import com.haruUp.missionembedding.service.MissionRecommendationService
import com.haruUp.missionembedding.service.TodayMissionCacheService
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
    private val todayMissionCacheService: TodayMissionCacheService,
    private val memberProfileRepository: MemberProfileRepository,
    private val memberMissionRepository: MemberMissionRepository,
    private val jobRepository: JobRepository,
    private val jobDetailRepository: JobDetailRepository,
    private val memberInterestRepository: MemberInterestJpaRepository
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    private val MAX_RETRY = 3
    private val RECOMMEND_LIMIT = 5

    /**
     * 오늘의 미션 추천 (캐시 우선)
     */
    fun recommend(memberId: Long): MissionRecommendResult {
        val today = LocalDate.now()
        val key = MissionRecommendRedisKey.recommend(memberId, today)

        redisTemplate.opsForValue().get(key)?.let {
            return objectMapper.readValue(it, MissionRecommendResult::class.java)
        }

        val result = generate(memberId)
        cache(key, result)
        return result
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

        val cachedMissionIds = todayMissionCacheService.getRecommendedMissionIds(
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

        // 6. 추천된 미션 ID를 Redis에 저장
        val recommendedMissionIds = missionDtos.mapNotNull { it.id }
        if (recommendedMissionIds.isNotEmpty()) {
            todayMissionCacheService.saveRecommendedMissionIds(
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
            seqNo = 1,
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
     * 추천 생성 핵심 로직
     */
    private fun generate(memberId: Long): MissionRecommendResult {
        // 1️⃣ 유저 임베딩 생성
        val userEmbedding = missionAiClient.createUserEmbedding(memberId)

        // 2️⃣ 벡터 유사도 기반 추천
        // TODO: 사용자의 실제 관심사 경로로 변경 필요
        val defaultDirectFullPath = "{LIFE}"  // PostgreSQL 배열 형식
        val candidates =
            missionEmbeddingRepository.findByVectorSimilarity(
                embedding = userEmbedding,
                directFullPath = defaultDirectFullPath,
                difficulty = null,
                limit = RECOMMEND_LIMIT * 2
            )

        // 3️⃣ DTO 변환
        val missions = candidates
            .take(RECOMMEND_LIMIT)
            .map {
                MissionCandidateDto(
                    embeddingMissionId = it.id!!,
                    content = it.missionContent,
                    directFullPath = it.directFullPath,
                    difficulty = it.difficulty,
                    reason = "최근 관심사와 유사한 미션이에요"
                )
            }

        return MissionRecommendResult(
            generatedAt = LocalDateTime.now(),
            missions = missions
        )
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
}


object MissionRecommendRedisKey {
    fun recommend(memberId: Long, date: LocalDate) =
        "mission:recommend:$memberId:$date"

    fun retry(memberId: Long, date: LocalDate) =
        "mission:recommend:retry:$memberId:$date"
}