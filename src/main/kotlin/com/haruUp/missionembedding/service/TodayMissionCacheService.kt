package com.haruUp.missionembedding.service

import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.LocalDate

/**
 * 오늘의 미션 추천 캐시 서비스
 *
 * Redis에 사용자별/관심사별 추천된 미션 ID를 저장하여
 * 재추천 시 중복 제외에 활용
 */
@Service
class TodayMissionCacheService(
    private val redisTemplate: RedisTemplate<String, Any>
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val KEY_PREFIX = "today-mission"
        private val TTL = Duration.ofHours(24)  // 24시간 후 자동 만료
    }

    /**
     * Redis 키 생성
     * 형식: today-mission:{memberId}:{interestId}:{date}
     */
    private fun buildKey(memberId: Long, interestId: Long): String {
        val today = LocalDate.now().toString()
        return "$KEY_PREFIX:$memberId:$interestId:$today"
    }

    /**
     * 추천된 미션 ID 목록 저장
     *
     * @param memberId 사용자 ID
     * @param interestId 관심사 ID
     * @param missionIds 추천된 미션 ID 목록
     */
    fun saveRecommendedMissionIds(memberId: Long, interestId: Long, missionIds: List<Long>) {
        val key = buildKey(memberId, interestId)
        try {
            // 기존 값에 추가
            val existingIds = getRecommendedMissionIds(memberId, interestId)
            val allIds = (existingIds + missionIds).distinct()

            // Set으로 저장
            redisTemplate.delete(key)
            if (allIds.isNotEmpty()) {
                redisTemplate.opsForSet().add(key, *allIds.map { it.toString() }.toTypedArray())
                redisTemplate.expire(key, TTL)
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
        val key = buildKey(memberId, interestId)
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
