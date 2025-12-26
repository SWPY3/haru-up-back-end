package com.haruUp.mission.infrastructure

import com.haruUp.mission.domain.MemberMissionEntity
import com.haruUp.mission.domain.MissionStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime

interface MemberMissionRepository : JpaRepository<MemberMissionEntity, Long> {

    /** * 사용자의 모든 미션 조회 */
    fun findByMemberId(memberId: Long): List<MemberMissionEntity>

    /** * 사용자의 삭제되지 않은 미션 조회 */
    fun findByMemberIdAndDeletedFalse(memberId: Long): List<MemberMissionEntity>

    /* 오늘의 추천 미션 조회 - mission_embeddings.difficulty 기준 */
    @Query(
        value = """
            SELECT mm.*
            FROM member_mission mm
            JOIN mission_embeddings me ON mm.mission_id = me.id
            WHERE mm.id IN (
                SELECT id FROM (
                    SELECT
                        m.id,
                        ROW_NUMBER() OVER (
                            PARTITION BY e.difficulty
                            ORDER BY
                                CASE WHEN m.create_at = CURRENT_DATE THEN 0 ELSE 1 END
                        ) AS rn
                    FROM member_mission m
                    JOIN mission_embeddings e ON m.mission_id = e.id
                    WHERE m.member_id = :memberId
                      AND m.mission_status <> 'COMPLETED'
                      AND (
                            (m.create_at = CURRENT_DATE)
                            OR
                            (m.mission_status = 'READY' AND m.created_at = CURRENT_DATE AND m.create_at IS NULL)
                          )
                ) sub
                WHERE sub.rn = 1
            )
            ORDER BY me.difficulty
        """,
        nativeQuery = true
    )
    fun getTodayMissionsByMemberId(memberId: Long): List<MemberMissionEntity>

    /**
     * 사용자의 ACTIVE 상태 미션 ID 목록 조회
     * 오늘의 미션 추천 시 제외할 미션 조회에 사용
     */
    @Query("""
    SELECT m.missionId
    FROM MemberMissionEntity m
    WHERE m.memberId = :memberId
      AND m.missionStatus = :status
    """)
    fun findMissionIdsByMemberIdAndStatus(
        memberId: Long,
        status: MissionStatus
    ): List<Long>

    /**
     * 특정 관심사의 READY 상태 미션을 soft delete 처리
     * 새로운 미션 추천 시 해당 관심사의 기존 READY 상태 미션들만 삭제 처리
     */
    @Transactional
    @Modifying
    @Query("""
    UPDATE MemberMissionEntity m
    SET m.deleted = true, m.deletedAt = :deletedAt
    WHERE m.memberId = :memberId
      AND m.memberInterestId = :memberInterestId
      AND m.missionStatus = :status
      AND m.deleted = false
    """)
    fun softDeleteByMemberIdAndInterestIdAndStatus(
        memberId: Long,
        memberInterestId: Long,
        status: MissionStatus,
        deletedAt: LocalDateTime
    ): Int

    /**
     * 특정 ID를 제외하고 soft delete
     */
    @Transactional
    @Modifying
    @Query("""
    UPDATE MemberMissionEntity m
    SET m.deleted = true, m.deletedAt = :deletedAt
    WHERE m.memberId = :memberId
      AND m.memberInterestId = :memberInterestId
      AND m.missionStatus = :status
      AND m.deleted = false
      AND m.id NOT IN :excludeIds
    """)
    fun softDeleteByMemberIdAndInterestIdAndStatusExcludingIds(
        memberId: Long,
        memberInterestId: Long,
        status: MissionStatus,
        excludeIds: List<Long>,
        deletedAt: LocalDateTime
    ): Int

    /**
     * 오늘의 미션 조회
     * - deleted = false
     * - targetDate = 오늘
     * - missionStatus IN (READY, ACTIVE)
     * - 특정 memberInterestId
     */
    @Query("""
    SELECT m FROM MemberMissionEntity m
    WHERE m.memberId = :memberId
      AND m.memberInterestId = :memberInterestId
      AND m.deleted = false
      AND m.targetDate = :targetDate
      AND m.missionStatus IN :statuses
    ORDER BY m.id
    """)
    fun findTodayMissions(
        memberId: Long,
        memberInterestId: Long,
        targetDate: LocalDate,
        statuses: List<MissionStatus>
    ): List<MemberMissionEntity>

    /**
     * memberId, memberInterestId, missionId로 미션 조회 (deleted=false)
     */
    fun findByMemberIdAndMemberInterestIdAndMissionIdAndDeletedFalse(
        memberId: Long,
        memberInterestId: Long,
        missionId: Long
    ): MemberMissionEntity?

    /**
     * 특정 사용자의 모든 미션 soft delete
     */
    @Transactional
    @Modifying
    @Query("""
    UPDATE MemberMissionEntity m
    SET m.deleted = true, m.deletedAt = CURRENT_TIMESTAMP
    WHERE m.memberId = :memberId
      AND m.deleted = false
    """)
    fun softDeleteAllByMemberId(memberId: Long): Int

    /**
     * 특정 사용자의 특정 관심사에 해당하는 모든 미션 soft delete
     * (상태 무관)
     */
    @Transactional
    @Modifying
    @Query("""
    UPDATE MemberMissionEntity m
    SET m.deleted = true, m.deletedAt = :deletedAt
    WHERE m.memberId = :memberId
      AND m.memberInterestId = :memberInterestId
      AND m.deleted = false
    """)
    fun softDeleteByMemberIdAndInterestId(
        memberId: Long,
        memberInterestId: Long,
        deletedAt: LocalDateTime
    ): Int
}