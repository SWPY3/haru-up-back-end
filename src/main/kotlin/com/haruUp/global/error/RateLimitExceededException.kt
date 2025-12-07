package com.haruUp.global.error

/**
 * API 호출 횟수 제한 초과 예외
 *
 * @property limit 일일 허용 횟수
 * @property currentCount 현재 호출 횟수
 * @property resetAfterSeconds 리셋까지 남은 초
 */
class RateLimitExceededException(
    val limit: Int,
    val currentCount: Int,
    val resetAfterSeconds: Long,
    message: String = "일일 API 호출 횟수(${limit}회)를 초과했습니다."
) : BusinessException(ErrorCode.RATE_LIMIT_EXCEEDED, message)
