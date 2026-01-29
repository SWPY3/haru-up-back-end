package com.haruUp.mission.domain

import java.time.LocalDate

data class DailyMissionCountDto(
    val targetDate: LocalDate,
    val completedCount: Long,
    val isAttendance: Boolean = false
) {
    // Repository JPQL용 보조 생성자
    constructor(targetDate: LocalDate, completedCount: Long) : this(targetDate, completedCount, false)
}