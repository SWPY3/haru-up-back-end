package com.haruUp.mission.domain

data class MonthlyMissionDataDto(
    val missionCounts: List<DailyMissionCountDto>,
    val totalMissionCount: Long,
    val totalCompletedDays: Int  // 미션 완료한 날 수
)
