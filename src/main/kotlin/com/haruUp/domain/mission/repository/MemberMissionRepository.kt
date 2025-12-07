package com.haruUp.domain.mission.repository

import com.haruUp.domain.mission.entity.MemberMissionEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

/**
 * 사용자-미션 연결 Repository
 */
@Repository
interface MemberMissionRepository : JpaRepository<MemberMissionEntity, Long> {
    /**
     * 사용자와 미션으로 조회
     */
    fun findByMemberIdAndMissionId(memberId: Long, missionId: Long): MemberMissionEntity?

    /**
     * 사용자의 모든 미션 조회
     */
    fun findByMemberId(memberId: Long): List<MemberMissionEntity>
}
