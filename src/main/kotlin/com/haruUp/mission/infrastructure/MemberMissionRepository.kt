package com.haruUp.mission.infrastructure

import com.haruUp.mission.domain.MemberMission
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDateTime

interface MemberMissionRepository : JpaRepository<MemberMission, Long> {

    /** * 사용자와 미션으로 조회 */
    fun findByMemberIdAndMissionId(memberId: Long, missionId: Long): MemberMission?

    /** * 사용자의 모든 미션 조회 */
    fun findByMemberId(memberId: Long): List<MemberMission>

    /** * 오늘 생성된 미션 조회 */
    fun findByMemberIdAndCreatedAtBetween(memberId: Long, startOfDay: LocalDateTime, endOfDay: LocalDateTime) : List<MemberMission>
}