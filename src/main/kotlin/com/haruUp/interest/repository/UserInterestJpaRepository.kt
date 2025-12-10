package com.haruUp.interest.repository

import com.haruUp.interest.model.InterestPath
import org.springframework.stereotype.Repository
import java.util.concurrent.ConcurrentHashMap

/**
 * UserInterestRepository의 임시 구현체
 *
 * TODO: 실제 DB 저장소로 변경 필요
 */
@Repository
class UserInterestJpaRepository : UserInterestRepository {

    // 임시 in-memory 저장소
    private val userInterests = ConcurrentHashMap<Long, MutableList<InterestPath>>()

    override fun findByUserId(userId: Long): List<InterestPath> {
        return userInterests[userId] ?: emptyList()
    }

    override fun save(userId: Long, interestPath: InterestPath) {
        userInterests.computeIfAbsent(userId) { mutableListOf() }.add(interestPath)
    }

    override fun deleteByUserId(userId: Long) {
        userInterests.remove(userId)
    }
}
