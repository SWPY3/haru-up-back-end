package com.haruUp.global.ratelimit

import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Rate Limiting 서비스
 *
 * Redis를 사용하여 사용자별 API 호출 횟수를 추적하고 제한합니다.
 */
@Service
class RateLimitService(
    private val redisTemplate: RedisTemplate<String, Any>
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * API 호출 가능 여부 확인 및 카운트 증가
     *
     * @param userId 사용자 ID
     * @param key Redis 키 프리픽스
     * @param limit 일일 허용 횟수
     * @return 호출 가능 여부
     */
    fun checkAndIncrement(userId: Long, key: String, limit: Int): RateLimitResult {
        val redisKey = buildRedisKey(userId, key)

        // 현재 호출 횟수 조회
        val currentCount = (redisTemplate.opsForValue().get(redisKey) as? String)?.toIntOrNull() ?: 0

        logger.debug("Rate limit check - userId: $userId, key: $key, current: $currentCount, limit: $limit")

        // 제한 초과 확인
        if (currentCount >= limit) {
            val ttl = redisTemplate.getExpire(redisKey)
            logger.warn("Rate limit exceeded - userId: $userId, key: $key, count: $currentCount, limit: $limit")
            return RateLimitResult(
                allowed = false,
                currentCount = currentCount,
                limit = limit,
                resetAfterSeconds = ttl ?: 0
            )
        }

        // 카운트 증가
        val newCount = redisTemplate.opsForValue().increment(redisKey) ?: 1

        // 첫 번째 호출이면 만료 시간 설정 (자정까지)
        if (newCount == 1L) {
            val ttl = getSecondsUntilMidnight()
            redisTemplate.expire(redisKey, Duration.ofSeconds(ttl))
            logger.debug("Set TTL for new key: $redisKey, ttl: $ttl seconds")
        }

        val ttl = redisTemplate.getExpire(redisKey)
        logger.debug("Rate limit allowed - userId: $userId, key: $key, count: $newCount, limit: $limit")

        return RateLimitResult(
            allowed = true,
            currentCount = newCount.toInt(),
            limit = limit,
            resetAfterSeconds = ttl ?: 0
        )
    }

    /**
     * Redis 키 생성
     *
     * 형식: ratelimit:{key}:{userId}:{date}
     * 예: ratelimit:api:recommend:missions:123:2025-12-07
     */
    private fun buildRedisKey(userId: Long, key: String): String {
        val date = LocalDate.now().toString()
        return "ratelimit:$key:$userId:$date"
    }

    /**
     * 자정까지 남은 초 계산
     */
    private fun getSecondsUntilMidnight(): Long {
        val now = LocalDateTime.now()
        val midnight = now.toLocalDate().plusDays(1).atStartOfDay()
        return Duration.between(now, midnight).seconds
    }

    /**
     * 특정 사용자의 특정 API 호출 횟수 조회
     */
    fun getCurrentCount(userId: Long, key: String): Int {
        val redisKey = buildRedisKey(userId, key)
        return (redisTemplate.opsForValue().get(redisKey) as? String)?.toIntOrNull() ?: 0
    }

    /**
     * 특정 사용자의 특정 API 제한 초기화 (테스트용)
     */
    fun reset(userId: Long, key: String) {
        val redisKey = buildRedisKey(userId, key)
        redisTemplate.delete(redisKey)
        logger.info("Rate limit reset - userId: $userId, key: $key")
    }
}

/**
 * Rate Limit 체크 결과
 */
data class RateLimitResult(
    val allowed: Boolean,
    val currentCount: Int,
    val limit: Int,
    val resetAfterSeconds: Long
)
