package com.haruUp.mission.infrastructure

import com.haruUp.mission.domain.MemberCustomMissionEntity
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

interface MemberCustomMissionRepository : JpaRepository<MemberCustomMissionEntity, Long> {

    fun findByIdAndDeletedFalse(id: Long): MemberCustomMissionEntity?

    @Query(
        value = """
            SELECT * FROM member_custom_mission
            WHERE member_id = :memberId
              AND deleted = false
              AND (:type IS NULL OR type = CAST(:type AS VARCHAR))
              AND (:targetDate IS NULL OR target_date = CAST(:targetDate AS DATE))
            ORDER BY created_at DESC
        """,
        countQuery = """
            SELECT COUNT(*) FROM member_custom_mission
            WHERE member_id = :memberId
              AND deleted = false
              AND (:type IS NULL OR type = CAST(:type AS VARCHAR))
              AND (:targetDate IS NULL OR target_date = CAST(:targetDate AS DATE))
        """,
        nativeQuery = true
    )
    fun findMissions(
        memberId: Long,
        type: String?,
        targetDate: String?,
        pageable: Pageable
    ): Page<MemberCustomMissionEntity>

    @Query(
        value = """
            SELECT * FROM member_custom_mission
            WHERE member_id = :memberId
              AND deleted = false
              AND (:type IS NULL OR type = CAST(:type AS VARCHAR))
              AND (:targetDate IS NULL OR target_date = CAST(:targetDate AS DATE))
              AND mission_status IN (:statuses)
            ORDER BY created_at DESC
        """,
        countQuery = """
            SELECT COUNT(*) FROM member_custom_mission
            WHERE member_id = :memberId
              AND deleted = false
              AND (:type IS NULL OR type = CAST(:type AS VARCHAR))
              AND (:targetDate IS NULL OR target_date = CAST(:targetDate AS DATE))
              AND mission_status IN (:statuses)
        """,
        nativeQuery = true
    )
    fun findMissionsWithStatuses(
        memberId: Long,
        type: String?,
        targetDate: String?,
        statuses: List<String>,
        pageable: Pageable
    ): Page<MemberCustomMissionEntity>

    @Transactional
    @Modifying
    @Query(
        value = """
            UPDATE member_custom_mission
            SET deleted = true, deleted_at = :deletedAt
            WHERE member_id = :memberId AND deleted = false
        """,
        nativeQuery = true
    )
    fun softDeleteAllByMemberId(memberId: Long, deletedAt: LocalDateTime = LocalDateTime.now()): Int
}
