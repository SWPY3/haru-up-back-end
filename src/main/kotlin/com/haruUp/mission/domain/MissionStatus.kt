package com.haruUp.mission.domain

enum class MissionStatus {
    READY,        // 미션 시작 전 상태
    ACTIVE,       // 정상적으로 수행 중
    POSTPONED,    // 내일로 미루기
    COMPLETED,    // 완료
    INACTIVE      // 사용 기한 지나거나 시스템이 비활성화한 경우
}
