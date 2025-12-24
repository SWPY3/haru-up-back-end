package com.haruUp.interest.repository

import com.haruUp.interest.entity.MemberInterestEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

/**
 * 멤버-관심사 Repository
 */
@Repository
interface MemberInterestJpaRepository : JpaRepository<MemberInterestEntity, Long> {
    /**
     * 특정 사용자의 모든 관심사 조회 (삭제되지 않은 것)
     */
    fun findByMemberIdAndDeletedFalse(memberId: Long): List<MemberInterestEntity>

    /**
     * 특정 사용자의 특정 관심사 조회 (삭제되지 않은 것)
     */
    fun findByIdAndMemberIdAndDeletedFalse(id: Long, memberId: Long): MemberInterestEntity?

    /**
     * 특정 사용자의 모든 관심사 소프트 삭제
     */
    @Modifying
    @Query("UPDATE MemberInterestEntity m SET m.deleted = true, m.deletedAt = CURRENT_TIMESTAMP WHERE m.memberId = :memberId")
    fun softDeleteAllByMemberId(memberId: Long): Int

    /**
     * 특정 관심사 소프트 삭제
     */
    @Modifying
    @Query("UPDATE MemberInterestEntity m SET m.deleted = true, m.deletedAt = CURRENT_TIMESTAMP WHERE m.id = :id AND m.memberId = :memberId")
    fun softDeleteByIdAndMemberId(id: Long, memberId: Long): Int
}
