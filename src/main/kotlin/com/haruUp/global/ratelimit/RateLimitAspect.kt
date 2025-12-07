package com.haruUp.global.ratelimit

import com.haruUp.global.error.RateLimitExceededException
import com.haruUp.global.security.SecurityUtils
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.reflect.MethodSignature
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Rate Limit AOP
 *
 * @RateLimit 어노테이션이 적용된 메서드의 호출 횟수를 제한합니다.
 */
@Aspect
@Component
class RateLimitAspect(
    private val rateLimitService: RateLimitService,
    private val securityUtils: SecurityUtils
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Around("@annotation(com.haruUp.global.ratelimit.RateLimit)")
    fun checkRateLimit(joinPoint: ProceedingJoinPoint): Any? {
        // 메서드에서 @RateLimit 어노테이션 추출
        val signature = joinPoint.signature as MethodSignature
        val method = signature.method
        val rateLimit = method.getAnnotation(RateLimit::class.java)

        // 현재 로그인한 사용자 ID 가져오기
        val userId = try {
            securityUtils.getCurrentMemberId() ?: run {
                logger.warn("Rate limit check failed: 인증되지 않은 사용자")
                // 인증되지 않은 경우 통과 (SecurityConfig에서 처리됨)
                return joinPoint.proceed()
            }
        } catch (e: Exception) {
            logger.warn("Rate limit check failed: ${e.message}")
            // 인증되지 않은 경우 통과 (SecurityConfig에서 처리됨)
            return joinPoint.proceed()
        }

        logger.debug("Rate limit check - userId: $userId, key: ${rateLimit.key}, limit: ${rateLimit.limit}")

        // Rate limit 체크
        val result = rateLimitService.checkAndIncrement(
            userId = userId,
            key = rateLimit.key,
            limit = rateLimit.limit
        )

        if (!result.allowed) {
            logger.warn("Rate limit exceeded - userId: $userId, key: ${rateLimit.key}, count: ${result.currentCount}/${result.limit}")
            throw RateLimitExceededException(
                limit = result.limit,
                currentCount = result.currentCount,
                resetAfterSeconds = result.resetAfterSeconds
            )
        }

        logger.debug("Rate limit allowed - userId: $userId, key: ${rateLimit.key}, count: ${result.currentCount}/${result.limit}")

        // 원래 메서드 실행
        return joinPoint.proceed()
    }
}
