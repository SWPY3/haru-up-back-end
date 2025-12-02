package com.haruUp.interest.infrastructure

import com.haruUp.interest.domain.Interest
import org.springframework.data.jpa.repository.JpaRepository

interface InterestRepository : JpaRepository<Interest, Long> {

    // parent_id로 자식 관심사 조회 (삭제되지 않은 것만)
    fun findByParentIdAndDeletedFalse(parentId: Long?): List<Interest>

    // depth로 관심사 조회 (삭제되지 않은 것만)
    fun findByDepthAndDeletedFalse(depth: Int): List<Interest>

    // depth와 parent_id로 관심사 조회
    fun findByDepthAndParentIdAndDeletedFalse(depth: Int, parentId: Long?): List<Interest>
}
