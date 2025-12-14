package com.haruUp.mission.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.haruUp.domain.mission.repository.MissionEmbeddingRepository
import com.haruUp.mission.domain.MissionCandidateDto
import com.haruUp.mission.domain.MissionRecommendResult
import com.haruUp.mission.infrastructure.MemberMissionRepository
import com.haruUp.mission.infrastructure.MissionAiClient
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.jvm.java

@Service
class MissionRecommendService(
    private val redisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper,
    private val missionEmbeddingRepository: MissionEmbeddingRepository,
    private val memberMissionRepository: MemberMissionRepository,
    private val missionAiClient: MissionAiClient
) {

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
     * 재추천
     */
    fun retry(memberId: Long): MissionRecommendResult {
        val today = LocalDate.now()
        val retryKey = MissionRecommendRedisKey.retry(memberId, today)

        val count = redisTemplate.opsForValue().increment(retryKey) ?: 0
        if (count > MAX_RETRY) {
            throw IllegalStateException("재추천 가능 횟수를 초과했습니다.")
        }

        val key = MissionRecommendRedisKey.recommend(memberId, today)
        val result = generate(memberId)
        cache(key, result)
        return result
    }

    /**
     * 추천 생성 핵심 로직
     */
    private fun generate(memberId: Long): MissionRecommendResult {
        // 1️⃣ 유저 임베딩 생성
        val userEmbedding = missionAiClient.createUserEmbedding(memberId)

        // 2️⃣ 오늘 이미 확정한 미션 content 제외
// 2️⃣ 오늘 이미 확정한 미션 ID 제외
        val todayMissionIds =
            memberMissionRepository.findMissionIdsByMemberIdAndDate(
                memberId,
                LocalDate.now()
            )

// 3️⃣ 벡터 유사도 기반 추천
        val candidates =
            missionEmbeddingRepository.findByVectorSimilarity(
                embedding = userEmbedding,
                mainCategory = "LIFE",
                middleCategory = null,
                subCategory = null,
                difficulty = null,
                limit = RECOMMEND_LIMIT * 2
            )

// 4️⃣ 필터링 + DTO 변환
        val missions = candidates
            .filterNot { it.id in todayMissionIds }   // 🔥 정확한 키 비교
            .take(RECOMMEND_LIMIT)
            .map {
                MissionCandidateDto(
                    embeddingMissionId = it.id!!,
                    content = it.missionContent,
                    mainCategory = it.mainCategory,
                    middleCategory = it.middleCategory,
                    subCategory = it.subCategory,
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