package com.haruUp.domain.interest.repository

import com.haruUp.domain.interest.entity.MemberInterestEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

/**
 * 멤버-관심사 Repository
 */
@Repository
interface MemberInterestJpaRepository : JpaRepository<MemberInterestEntity, Long> {
    /**
     * 특정 사용자의 모든 관심사 조회
     */
    fun findByMemberId(memberId: Long): List<MemberInterestEntity>

    /**
     * 특정 사용자의 특정 관심사 조회
     */
    fun findByMemberIdAndInterestId(memberId: Long, interestId: Long): MemberInterestEntity?

    /**
     * 특정 사용자의 관심사 존재 여부 확인
     */
    fun existsByMemberIdAndInterestId(memberId: Long, interestId: Long): Boolean
}
