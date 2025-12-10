package com.haruUp.interest.repository

import com.haruUp.interest.entity.Interest
import org.springframework.data.jpa.repository.JpaRepository

interface InterestEntityJpaRepository : JpaRepository<Interest, Long> {

    // parent_id로 자식 관심사 조회 (삭제되지 않은 것만)
    fun findByParentIdAndDeletedFalse(parentId: Long?): List<Interest>

    // level로 관심사 조회 (삭제되지 않은 것만)
    fun findByLevelAndDeletedFalse(level: String): List<Interest>

    // level과 parent_id로 관심사 조회
    fun findByLevelAndParentIdAndDeletedFalse(level: String, parentId: Long?): List<Interest>
}
