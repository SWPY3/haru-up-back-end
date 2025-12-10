package com.haruUp.mission.domain

enum class MissionStatus {

    ACTIVE,       // 정상적으로 수행 중
    POSTPONED,    // 사용자가 내일로 미룸
    COMPLETED,    // 완료
    INACTIVE      // 사용 기한 지나거나 시스템이 비활성화한 경우
}
