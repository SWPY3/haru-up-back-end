package com.haruUp.mission.domain

data class MonthlyAttendanceDto(
    val targetMonth: String,
    val attendanceCount: Int
)

data class MonthlyAttendanceResponseDto(
    val attendanceDates: List<MonthlyAttendanceDto>
)
