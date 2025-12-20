package com.haruUp.mission.infrastructure

import com.haruUp.mission.domain.MemberMission
import com.haruUp.mission.domain.MissionStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.LocalDate
import java.time.LocalDateTime

interface MemberMissionRepository : JpaRepository<MemberMission, Long> {

    /** * 사용자의 모든 미션 조회 */
    fun findByMemberId(memberId: Long): List<MemberMission>

    /* 오늘의 추천 미션 조회*/
    @Query(
        value = """
            SELECT *
            FROM member_mission
            WHERE id IN (
                SELECT id FROM (
                    SELECT
                        m.id,
                        ROW_NUMBER() OVER (
                            PARTITION BY m.mission_level
                            ORDER BY
                                CASE WHEN m.postponed_at = CURRENT_DATE THEN 0 ELSE 1 END
                        ) AS rn
                    FROM member_mission m
                    WHERE m.member_id = :memberId
                      AND m.mission_status <> 'COMPLETED'
                      AND (
                            (m.postponed_at = CURRENT_DATE)
                            OR
                            (m.mission_status = 'READY' AND m.create_at = CURRENT_DATE AND m.postponed_at IS NULL)
                          )
                ) sub
                WHERE sub.rn = 1
            )
            ORDER BY mission_level
        """,
        nativeQuery = true
    )
    fun getTodayMissionsByMemberId(memberId: Long): List<MemberMission>

    @Query("""
    SELECT m.missionId
    FROM MemberMission m
    WHERE m.memberId = :memberId
      AND m.targetDate = :targetDate
    """)
    fun findMissionIdsByMemberIdAndDate(
        memberId: Long,
        targetDate: LocalDate
    ): List<Long>

    /**
     * 사용자의 ACTIVE 상태 미션 ID 목록 조회
     * 오늘의 미션 추천 시 제외할 미션 조회에 사용
     */
    @Query("""
    SELECT m.missionId
    FROM MemberMission m
    WHERE m.memberId = :memberId
      AND m.missionStatus = :status
    """)
    fun findMissionIdsByMemberIdAndStatus(
        memberId: Long,
        status: MissionStatus
    ): List<Long>
}