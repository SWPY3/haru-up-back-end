package com.haruUp.mission.infrastructure

import com.haruUp.mission.domain.DailyMissionCountDto
import com.haruUp.mission.domain.MemberMissionEntity
import com.haruUp.mission.domain.MissionStatus
import com.haruUp.notification.domain.MissionPushTarget
import java.time.LocalDate
import java.time.LocalDateTime

interface MemberMissionRepositoryCustom {
    fun getTodayMissionsByMemberId(memberId: Long): List<MemberMissionEntity>

    fun findDifficultiesByMemberIdAndStatus(memberId: Long, status: MissionStatus): List<Int>

    fun findMissionContentsByMemberIdAndStatus(memberId: Long, status: MissionStatus): List<String>

    fun softDeleteByMemberIdAndInterestIdAndStatus(
        memberId: Long,
        memberInterestId: Long,
        status: MissionStatus,
        deletedAt: LocalDateTime
    ): Int

    fun softDeleteByMemberIdAndInterestIdAndStatusExcludingIds(
        memberId: Long,
        memberInterestId: Long,
        status: MissionStatus,
        excludeIds: List<Long>,
        deletedAt: LocalDateTime
    ): Int

    fun findTodayMissions(
        memberId: Long,
        memberInterestId: Long,
        targetDate: LocalDate,
        statuses: List<MissionStatus>
    ): List<MemberMissionEntity>

    fun findCompletedDatesByMemberIdAndDateRange(
        memberId: Long,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<LocalDate>

    fun softDeleteAllByMemberId(memberId: Long): Int

    fun softDeleteByMemberIdAndInterestId(
        memberId: Long,
        memberInterestId: Long,
        deletedAt: LocalDateTime
    ): Int

    fun findSelectedMissionsByTargetDate(targetDate: LocalDate): List<MemberMissionEntity>

    fun updateLabelNameAndEmbedding(
        id: Long,
        labelName: String,
        embedding: String,
        updatedAt: LocalDateTime
    ): Int

    fun updateLabelName(
        id: Long,
        labelName: String,
        updatedAt: LocalDateTime
    ): Int

    fun findSimilarMission(embedding: String, threshold: Double): MemberMissionEntity?

    fun findSelectedMissionsWithoutLabel(targetDate: LocalDate): List<MemberMissionEntity>

    fun findDailyCompletedMissionCount(
        memberId: Long,
        targetStartDate: LocalDate,
        targetEndDate: LocalDate
    ): List<DailyMissionCountDto>

    fun countTodaySelectedMissions(
        memberId: Long,
        targetDate: LocalDate,
        statuses: List<MissionStatus>
    ): Long

    fun findMembersWithTodayFalseMission(
        atStartOfDay: LocalDateTime,
        atEndOfDay: LocalDateTime
    ): List<MissionPushTarget>
}
