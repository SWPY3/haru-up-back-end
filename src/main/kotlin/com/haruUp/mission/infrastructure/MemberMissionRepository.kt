package com.haruUp.mission.infrastructure

import com.haruUp.mission.domain.MemberMission
import com.haruUp.mission.domain.MissionStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface MemberMissionRepository : JpaRepository<MemberMission, Long> {

    /** * 사용자의 모든 미션 조회 */
    fun findByMemberId(memberId: Long): List<MemberMission>

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
                                CASE WHEN m.postponed_at = CURRENT_DATE THEN 0 ELSE 1 END
                        ) AS rn
                    FROM member_mission m
                    JOIN mission_embeddings e ON m.mission_id = e.id
                    WHERE m.member_id = :memberId
                      AND m.mission_status <> 'COMPLETED'
                      AND (
                            (m.postponed_at = CURRENT_DATE)
                            OR
                            (m.mission_status = 'READY' AND m.created_at = CURRENT_DATE AND m.postponed_at IS NULL)
                          )
                ) sub
                WHERE sub.rn = 1
            )
            ORDER BY me.difficulty
        """,
        nativeQuery = true
    )
    fun getTodayMissionsByMemberId(memberId: Long): List<MemberMission>

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