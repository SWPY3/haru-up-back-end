package com.haruUp.interest.repository

import com.haruUp.interest.model.InterestNode
import org.springframework.stereotype.Repository
import java.util.concurrent.ConcurrentHashMap

/**
 * EmbeddingQueueRepository의 임시 구현체
 *
 * TODO: 실제 DB 저장소로 변경 필요
 */
@Repository
class EmbeddingQueueJpaRepository : EmbeddingQueueRepository {

    // 임시 in-memory 큐
    private val queue = ConcurrentHashMap<String, InterestNode>()

    override fun save(interest: InterestNode) {
        queue[interest.id] = interest
    }

    override fun findAll(): List<InterestNode> {
        return queue.values.toList()
    }

    override fun delete(interest: InterestNode) {
        queue.remove(interest.id)
    }

    override fun deleteById(interestId: String) {
        queue.remove(interestId)
    }
}
