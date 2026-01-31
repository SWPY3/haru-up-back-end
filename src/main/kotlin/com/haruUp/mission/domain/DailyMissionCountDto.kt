package com.haruUp.mission.domain

import java.time.LocalDate

data class DailyMissionCountDto(
    val targetDate: LocalDate,
    val completedCount: Long
)