package com.haruUp.mission.domain

data class MonthlyMissionWithAttendanceDto(
    val missionCounts: List<DailyMissionCountDto>,
    val totalMissionCount: Long,
    val totalAttendanceCount: Int
)
