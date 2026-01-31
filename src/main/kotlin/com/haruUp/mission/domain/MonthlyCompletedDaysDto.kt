package com.haruUp.mission.domain

data class MonthlyCompletedDaysDto(
    val targetMonth: String,
    val completedDays: Int  // 해당 월에 미션 완료한 날 수
)

data class MonthlyCompletedDaysResponseDto(
    val monthlyData: List<MonthlyCompletedDaysDto>
)
