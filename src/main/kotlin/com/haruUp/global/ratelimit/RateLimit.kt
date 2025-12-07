package com.haruUp.global.ratelimit

/**
 * API 호출 횟수 제한 어노테이션
 *
 * 사용자별로 일일 API 호출 횟수를 제한합니다.
 *
 * @property key Redis 키 프리픽스 (예: "api:recommend:missions")
 * @property limit 일일 허용 횟수 (기본값: 50)
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class RateLimit(
    val key: String,
    val limit: Int = 50
)
