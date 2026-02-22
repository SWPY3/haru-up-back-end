package com.haruUp.mission.infrastructure

import com.haruUp.mission.domain.MemberCustomMissionEntity
import com.haruUp.mission.domain.MissionStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime

interface MemberCustomMissionRepository : JpaRepository<MemberCustomMissionEntity, Long> {

    fun findByMemberIdAndDeletedFalse(memberId: Long): List<MemberCustomMissionEntity>

    fun findByMemberIdAndTargetDateAndDeletedFalse(
        memberId: Long,
        targetDate: LocalDate
    ): List<MemberCustomMissionEntity>

    fun findByIdAndDeletedFalse(id: Long): MemberCustomMissionEntity?

    fun findByMemberIdAndMissionStatusAndDeletedFalse(
        memberId: Long,
        missionStatus: MissionStatus
    ): List<MemberCustomMissionEntity>

    @Transactional
    @Modifying
    @Query("""
        UPDATE MemberCustomMissionEntity m
        SET m.deleted = true, m.deletedAt = :deletedAt
        WHERE m.memberId = :memberId AND m.deleted = false
    """)
    fun softDeleteAllByMemberId(memberId: Long, deletedAt: LocalDateTime = LocalDateTime.now()): Int
}
