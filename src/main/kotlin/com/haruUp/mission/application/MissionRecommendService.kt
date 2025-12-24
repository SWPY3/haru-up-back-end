package com.haruUp.mission.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.haruUp.category.repository.JobDetailRepository
import com.haruUp.category.repository.JobRepository
import com.haruUp.global.clova.MissionMemberProfile
import com.haruUp.interest.dto.InterestPath
import com.haruUp.interest.repository.InterestEmbeddingJpaRepository
import com.haruUp.interest.repository.MemberInterestJpaRepository
import com.haruUp.member.infrastructure.MemberProfileRepository
import com.haruUp.mission.domain.MemberMissionEntity
import com.haruUp.mission.domain.MissionCandidateDto
import com.haruUp.mission.domain.MissionExpCalculator
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
    private val missionRecommendationService: MissionRecommendationService,
    private val memberProfileRepository: MemberProfileRepository,
    private val memberMissionRepository: MemberMissionRepository,
    private val jobRepository: JobRepository,
    private val jobDetailRepository: JobDetailRepository,
    private val memberInterestRepository: MemberInterestJpaRepository,
    private val interestEmbeddingRepository: InterestEmbeddingJpaRepository
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val TODAY_RETRY_TTL = Duration.ofHours(24)  // 24시간 후 자동 만료

    /**
     * 미션 추천 조회
     *
     * member_mission에서 조건에 맞는 미션 조회:
     * - deleted = false
     * - targetDate = 지정된 날짜
     * - missionStatus IN (READY, ACTIVE, POSTPONED)
     *
     * @param memberId 멤버 ID
     * @param memberInterestId 멤버 관심사 ID
     * @param targetDate 조회할 날짜
     */
    fun recommend(memberId: Long, memberInterestId: Long, targetDate: LocalDate): MissionRecommendResult {
        // member_mission에서 미션 조회
        val memberMissions = memberMissionRepository.findTodayMissions(
            memberId = memberId,
            memberInterestId = memberInterestId,
            targetDate = targetDate,
            statuses = listOf(MissionStatus.READY, MissionStatus.ACTIVE, MissionStatus.POSTPONED)
        )

        logger.info("미션 조회 - memberId: $memberId, memberInterestId: $memberInterestId, targetDate: $targetDate, 결과: ${memberMissions.size}개")

        // mission_embeddings에서 상세 정보 조회하여 MissionCandidateDto로 변환
        val missions = memberMissions.mapNotNull { memberMission ->
            missionEmbeddingRepository.findById(memberMission.missionId).orElse(null)?.let { embedding ->
                MissionCandidateDto(
                    memberMissionId = memberMission.id!!,
                    missionStatus = memberMission.missionStatus,
                    content = embedding.missionContent,
                    directFullPath = embedding.directFullPath,
                    difficulty = embedding.difficulty,
                    expEarned = memberMission.expEarned,
                    targetDate = memberMission.targetDate
                )
            }
        }

        return MissionRecommendResult(
            missions = missions,
            retryCount = getRetryCount(memberId)
        )
    }

    /**
     * 재추천 (memberInterestId 기반)
     *
     * 사용자 프로필과 관심사 정보를 기반으로 미션 재추천
     * - excludeMemberMissionIds에 해당하는 미션의 난이도는 제외하고 추천
     * - 제외된 미션은 soft delete 하지 않고 유지
     * - 재추천된 미션만 READY 상태로 저장
     * - 추천된 미션 ID는 Redis에 캐싱 (24시간)
     * - reset_mission_count 카운트 증가
     *
     * @param excludeMemberMissionIds 제외할 member_mission ID 목록 (해당 난이도는 재추천에서 제외)
     */
    suspend fun retryWithInterest(
        memberId: Long,
        memberInterestId: Long,
        excludeMemberMissionIds: List<Long>? = null
    ): MissionRecommendationResponse {
        if (getRetryCount(memberId) >= 5) {
            throw IllegalArgumentException("재추천 횟수 초과: 최대 5회까지 가능합니다.")
        }

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

        // 3-2. interest_embeddings에서 fullPath 조회
        val interestFullPath = interestEmbeddingRepository.findEntityById(memberInterest.interestId)?.fullPath
        logger.info("interest_embeddings fullPath: $interestFullPath")

        // 4. 제외할 난이도 조회 (excludeMemberMissionIds에 해당하는 미션들의 난이도)
        val excludeDifficulties = if (!excludeMemberMissionIds.isNullOrEmpty()) {
            val excludedMemberMissions = memberMissionRepository.findAllById(excludeMemberMissionIds)
            val missionIds = excludedMemberMissions.map { it.missionId }
            val difficulties = missionEmbeddingRepository.findAllById(missionIds)
                .mapNotNull { it.difficulty }
                .distinct()
            logger.info("제외할 난이도: $difficulties (member_mission_ids: $excludeMemberMissionIds)")
            difficulties
        } else {
            emptyList()
        }

        // 5. 추천할 난이도 결정 (1~5 중 제외할 난이도를 뺀 나머지)
        val targetDifficulties = (1..5).filter { it !in excludeDifficulties }
        logger.info("추천할 난이도: $targetDifficulties")

        if (targetDifficulties.isEmpty()) {
            logger.info("추천할 난이도가 없습니다. 빈 응답 반환")
            return MissionRecommendationResponse(
                missions = listOf(
                    com.haruUp.missionembedding.dto.MissionGroupDto(
                        memberInterestId = memberInterestId.toInt(),
                        data = emptyList()
                    )
                ),
                totalCount = 0,
                retryCount = getRetryCount(memberId)
            )
        }

        // 6. 제외할 미션 ID 수집 (ACTIVE 상태 + Redis 캐시)
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

        // 7. 미션 추천 (targetDifficulties에 해당하는 난이도만)
        val missionDtos = missionRecommendationService.recommendTodayMissions(
            interestPath = interestPath,
            memberProfile = missionMemberProfile,
            difficulties = targetDifficulties,
            excludeIds = excludeIds
        )

        // 8. 제외된 미션을 제외한 기존 READY 상태 member_mission soft delete
        val excludeMemberMissionIdSet = excludeMemberMissionIds?.toSet() ?: emptySet()
        if (excludeMemberMissionIdSet.isEmpty()) {
            // 제외할 미션이 없으면 기존처럼 전체 READY 상태 soft delete
            val deletedCount = memberMissionRepository.softDeleteByMemberIdAndInterestIdAndStatus(
                memberId = memberId,
                memberInterestId = memberInterestId,
                status = MissionStatus.READY,
                deletedAt = LocalDateTime.now()
            )
            logger.info("기존 READY 상태 미션 soft delete 완료 (memberInterestId: $memberInterestId): ${deletedCount}개")
        } else {
            // 제외할 미션을 제외하고 나머지 READY 상태만 soft delete
            val deletedCount = memberMissionRepository.softDeleteByMemberIdAndInterestIdAndStatusExcludingIds(
                memberId = memberId,
                memberInterestId = memberInterestId,
                status = MissionStatus.READY,
                excludeIds = excludeMemberMissionIdSet.toList(),
                deletedAt = LocalDateTime.now()
            )
            logger.info("기존 READY 상태 미션 soft delete 완료 (제외: $excludeMemberMissionIdSet): ${deletedCount}개")
        }

        // 9. 추천된 미션들을 member_mission에 READY 상태로 저장
        val savedMemberMissions = missionDtos.mapNotNull { missionDto ->
            missionDto.mission_id?.let { missionId ->
                try {
                    val memberMission = MemberMissionEntity(
                        memberId = memberId,
                        missionId = missionId,
                        memberInterestId = memberInterestId,
                        missionStatus = MissionStatus.READY,
                        expEarned = MissionExpCalculator.calculateByDifficulty(missionDto.difficulty)
                    )
                    memberMissionRepository.save(memberMission)
                } catch (e: Exception) {
                    logger.error("member_mission 저장 실패: missionId=$missionId, 에러: ${e.message}")
                    null
                }
            }
        }
        logger.info("member_mission READY 상태로 저장 완료: ${savedMemberMissions.size}개")

        // 10. 추천된 미션 ID를 Redis에 저장
        val recommendedMissionIds = missionDtos.mapNotNull { it.mission_id }
        if (recommendedMissionIds.isNotEmpty()) {
            saveRecommendedMissionIds(
                memberId = memberId,
                interestId = memberInterest.interestId,
                missionIds = recommendedMissionIds
            )
        }

        // 11. reset_mission_count 증가
        memberInterest.incrementResetMissionCount()
        memberInterestRepository.save(memberInterest)
        logger.info("reset_mission_count 증가: ${memberInterest.resetMissionCount}")

        // 12. 응답 생성 - savedMemberMissions의 member_mission_id를 매핑
        val missionIdToMemberMissionId = savedMemberMissions.associateBy({ it.missionId }, { it.id })
        val missionDtosWithMemberMissionId = missionDtos.map { dto ->
            com.haruUp.missionembedding.dto.MissionDto(
                member_mission_id = dto.mission_id?.let { missionIdToMemberMissionId[it] },
                mission_id = dto.mission_id,
                content = dto.content,
                directFullPath = dto.directFullPath,
                fullPath = interestFullPath,
                difficulty = dto.difficulty,
                expEarned = MissionExpCalculator.calculateByDifficulty(dto.difficulty),
                createdType = dto.createdType
            )
        }

        val missionGroup = com.haruUp.missionembedding.dto.MissionGroupDto(
            memberInterestId = memberInterestId.toInt(),
            data = missionDtosWithMemberMissionId
        )

        logger.info("오늘의 미션 재추천 성공: ${missionDtosWithMemberMissionId.size}개")

        return MissionRecommendationResponse(
            missions = listOf(missionGroup),
            totalCount = missionDtosWithMemberMissionId.size,
            retryCount = incrementRetryCount(memberId)
        )
    }

    /**
     * 멤버 관심사 ID 목록 기반 미션 추천
     *
     * Controller와 Curation에서 공통으로 사용하는 미션 추천 로직
     * - 멤버 관심사 유효성 검증
     * - 사용자 프로필 조회
     * - 미션 추천 (각 관심사당 난이도 1~5)
     * - 기존 READY 상태 미션 soft delete
     * - 추천된 미션 저장
     *
     * @param memberId 멤버 ID
     * @param memberInterestIds 멤버 관심사 ID 목록
     * @return 추천된 미션 응답
     */
    fun recommendByMemberInterestIds(
        memberId: Long,
        memberInterestIds: List<Long>
    ): MissionRecommendationResponse {
        // 1. memberInterestIds 유효성 검증 및 조회
        val memberInterests = memberInterestIds.mapNotNull { memberInterestId ->
            val memberInterest = memberInterestRepository.findById(memberInterestId).orElse(null)
            if (memberInterest == null) {
                logger.warn("멤버 관심사를 찾을 수 없습니다: memberInterestId=$memberInterestId")
                return@mapNotNull null
            }
            if (memberInterest.memberId != memberId) {
                logger.warn("해당 관심사에 접근 권한이 없습니다: memberInterestId=$memberInterestId")
                return@mapNotNull null
            }
            memberInterest
        }

        require(memberInterests.isNotEmpty()) { "유효한 관심사가 없습니다." }

        // 2. DB에서 사용자 프로필 조회
        val memberProfileEntity = memberProfileRepository.findByMemberId(memberId)
            ?: throw IllegalArgumentException("사용자 프로필을 찾을 수 없습니다.")

        // 3. 직업 정보 조회
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

        logger.info("사용자 프로필 조회 완료 - 나이: ${missionMemberProfile.age}, 성별: ${missionMemberProfile.gender}, 직업: ${missionMemberProfile.jobName}")

        // 4. memberInterest에서 InterestPath 추출하여 (memberInterestId, InterestPath) 튜플로 변환
        // 4-1. interest_embeddings에서 fullPath 조회하여 맵 생성
        val memberInterestIdToFullPath = memberInterests.associate { memberInterest ->
            val fullPath = interestEmbeddingRepository.findEntityById(memberInterest.interestId)?.fullPath
            memberInterest.id!!.toInt() to fullPath
        }

        val interestsWithDetails = memberInterests.map { memberInterest ->
            val directFullPath = memberInterest.directFullPath
                ?: throw IllegalArgumentException("관심사 경로 정보가 없습니다: memberInterestId=${memberInterest.id}")

            val interestPath = InterestPath(
                mainCategory = directFullPath.getOrNull(0) ?: "",
                middleCategory = directFullPath.getOrNull(1),
                subCategory = directFullPath.getOrNull(2)
            )
            Pair(memberInterest.id!!.toInt(), interestPath)
        }

        // 5. 미션 추천 (각 관심사당 난이도 1~5 각각 1개씩)
        val missions = kotlinx.coroutines.runBlocking {
            missionRecommendationService.recommendMissions(
                interests = interestsWithDetails,
                memberProfile = missionMemberProfile
            )
        }

        // 6. 요청받은 관심사들의 기존 READY 상태 member_mission soft delete
        var totalDeletedCount = 0
        for (memberInterest in memberInterests) {
            val deletedCount = memberMissionRepository.softDeleteByMemberIdAndInterestIdAndStatus(
                memberId = memberId,
                memberInterestId = memberInterest.id!!,
                status = MissionStatus.READY,
                deletedAt = LocalDateTime.now()
            )
            totalDeletedCount += deletedCount
        }
        logger.info("기존 READY 상태 미션 soft delete 완료: ${totalDeletedCount}개")

        // 7. 추천된 미션들을 member_mission에 READY 상태로 저장하고 ID 수집
        val missionIdToMemberMissionId = mutableMapOf<Long, Long>()
        var savedMissionCount = 0
        for (memberInterest in memberInterests) {
            val memberInterestId = memberInterest.id!!.toInt()

            val missionGroup = missions.find { it.memberInterestId == memberInterestId }
            if (missionGroup == null) {
                logger.warn("memberInterestId=${memberInterestId}에 해당하는 미션 그룹을 찾을 수 없습니다.")
                continue
            }

            for (missionDto in missionGroup.data) {
                val missionId = missionDto.mission_id ?: continue
                try {
                    val memberMission = MemberMissionEntity(
                        memberId = memberId,
                        missionId = missionId,
                        memberInterestId = memberInterest.id!!,
                        missionStatus = MissionStatus.READY,
                        expEarned = MissionExpCalculator.calculateByDifficulty(missionDto.difficulty)
                    )
                    val saved = memberMissionRepository.save(memberMission)
                    saved.id?.let { missionIdToMemberMissionId[missionId] = it }
                    savedMissionCount++
                } catch (e: Exception) {
                    logger.error("member_mission 저장 실패: missionId=$missionId, 에러: ${e.message}")
                }
            }
        }
        logger.info("member_mission READY 상태로 저장 완료: ${savedMissionCount}개")

        // 8. 응답에 member_mission_id 매핑
        val missionsWithMemberMissionId = missions.map { group ->
            val groupFullPath = group.memberInterestId?.let { memberInterestIdToFullPath[it] }
            com.haruUp.missionembedding.dto.MissionGroupDto(
                memberInterestId = group.memberInterestId,
                data = group.data.map { dto ->
                    com.haruUp.missionembedding.dto.MissionDto(
                        member_mission_id = dto.mission_id?.let { missionIdToMemberMissionId[it] },
                        mission_id = dto.mission_id,
                        content = dto.content,
                        directFullPath = dto.directFullPath,
                        fullPath = groupFullPath,
                        difficulty = dto.difficulty,
                        expEarned = MissionExpCalculator.calculateByDifficulty(dto.difficulty),
                        createdType = dto.createdType
                    )
                }
            )
        }

        return MissionRecommendationResponse(
            missions = missionsWithMemberMissionId,
            totalCount = missionsWithMemberMissionId.sumOf { it.data.size }
        )
    }

    /**
     * 생년월일로부터 나이 계산
     */
    fun calculateAge(birthDt: LocalDateTime): Int {
        val birthDate = birthDt.toLocalDate()
        val now = LocalDateTime.now().toLocalDate()
        return Period.between(birthDate, now).years
    }

    /**
     * Redis 캐시
     */
     fun cache(key: String, value: MissionRecommendResult) {
        redisTemplate.opsForValue().set(
            key,
            objectMapper.writeValueAsString(value),
            Duration.ofSeconds(secondsUntilMidnight())
        )
    }

     fun secondsUntilMidnight(): Long {
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

    /**
     * 재추천 횟수 증가 (자정에 자동 만료)
     *
     * @param memberId 사용자 ID
     * @return 증가 후 현재 횟수
     */
    fun incrementRetryCount(memberId: Long): Long {
        val key = MissionRecommendRedisKey.retryCount(memberId)
        return try {
            val count = redisTemplate.opsForValue().increment(key) ?: 1L
            // TTL이 설정되지 않은 경우에만 설정 (첫 번째 증가 시)
            if (count == 1L) {
                redisTemplate.expire(key, Duration.ofSeconds(secondsUntilMidnight()))
            }
            logger.info("재추천 횟수 증가 - memberId: $memberId, count: $count")
            count
        } catch (e: Exception) {
            logger.error("재추천 횟수 증가 실패 - key: $key, error: ${e.message}")
            0L
        }
    }

    /**
     * 재추천 횟수 조회
     *
     * @param memberId 사용자 ID
     * @return 현재 재추천 횟수 (없으면 0)
     */
    fun getRetryCount(memberId: Long): Long {
        val key = MissionRecommendRedisKey.retryCount(memberId)
        return try {
            val count = redisTemplate.opsForValue().get(key)?.toLongOrNull() ?: 0L
            logger.info("재추천 횟수 조회 - memberId: $memberId, count: $count")
            count
        } catch (e: Exception) {
            logger.error("재추천 횟수 조회 실패 - key: $key, error: ${e.message}")
            0L
        }
    }

    /**
     * 재추천 횟수 초기화
     *
     * @param memberId 사용자 ID
     * @return 초기화 성공 여부
     */
    fun resetRetryCount(memberId: Long): Boolean {
        val key = MissionRecommendRedisKey.retryCount(memberId)
        return try {
            val deleted = redisTemplate.delete(key)
            logger.info("재추천 횟수 초기화 - memberId: $memberId, deleted: $deleted")
            deleted
        } catch (e: Exception) {
            logger.error("재추천 횟수 초기화 실패 - key: $key, error: ${e.message}")
            false
        }
    }
}


object MissionRecommendRedisKey {
    fun retry(memberId: Long, memberInterestId: Long, date: LocalDate) =
        "today-mission:$memberId:$memberInterestId:$date"

    fun retryCount(memberId: Long) =
        "mission:retry:count:$memberId"
}