package com.haruUp.interest.repository

import com.haruUp.interest.model.InterestLevel
import com.haruUp.interest.model.InterestNode
import com.haruUp.interest.model.InterestPath

/**
 * 관심사 Repository
 */
interface InterestRepository {
    fun save(interest: InterestNode): InterestNode
    fun findById(id: String): InterestNode?
    fun findByNameAndLevel(name: String, level: InterestLevel): InterestNode?
    fun findByPath(path: InterestPath): InterestNode?
    fun findByLevel(level: InterestLevel): List<InterestNode>
    fun findPopularByLevel(level: InterestLevel, limit: Int): List<InterestNode>
    fun findByParentId(parentId: String): List<InterestNode>
}

/**
 * Vector DB Repository
 *
 * pgvector (PostgreSQL extension)를 사용한 벡터 검색
 */
interface VectorInterestRepository {
    /**
     * 임베딩 데이터 삽입
     */
    suspend fun insert(
        interestId: String,
        name: String,
        level: InterestLevel,
        parentName: String?,
        fullPath: String,
        embedding: List<Float>,
        metadata: Map<String, Any>
    )

    /**
     * 유사 관심사 검색 (단일 쿼리)
     */
    suspend fun searchSimilar(
        query: String,
        level: InterestLevel,
        topK: Int = 10,
        minScore: Float = 0.7f
    ): List<InterestNode>

    /**
     * 유사 관심사 검색 (다중 쿼리)
     *
     * 여러 쿼리의 임베딩을 평균내어 검색
     */
    suspend fun searchSimilarMultiple(
        queries: List<String>,
        level: InterestLevel,
        topK: Int = 10,
        minScore: Float = 0.7f
    ): List<InterestNode>

    /**
     * 벡터로 직접 검색
     */
    suspend fun searchByVector(
        vector: List<Float>,
        level: InterestLevel,
        topK: Int = 10,
        minScore: Float = 0.7f
    ): List<InterestNode>

    /**
     * 하이브리드 검색 (유사도 + 인기도)
     *
     * score = similarity * 0.7 + (usageCount / maxUsageCount) * 0.3
     */
    suspend fun searchSimilarWithHybridScore(
        query: String,
        level: InterestLevel,
        topK: Int = 10,
        minScore: Float = 0.7f
    ): List<InterestNode>

    /**
     * 하이브리드 검색 (다중 쿼리, 유사도 + 인기도)
     */
    suspend fun searchSimilarMultipleWithHybridScore(
        queries: List<String>,
        level: InterestLevel,
        topK: Int = 10,
        minScore: Float = 0.7f
    ): List<InterestNode>

    /**
     * 특정 관심사 삭제
     */
    suspend fun delete(interestId: String)

    /**
     * 임베딩 업데이트
     */
    suspend fun update(
        interestId: String,
        embedding: List<Float>,
        metadata: Map<String, Any>
    )
}

/**
 * 임베딩 큐 Repository
 *
 * 임베딩 대기 중인 관심사 관리
 */
interface EmbeddingQueueRepository {
    fun save(interest: InterestNode)
    fun findAll(): List<InterestNode>
    fun delete(interest: InterestNode)
    fun deleteById(interestId: String)
}

/**
 * 사용자 관심사 Repository
 *
 * 사용자가 선택한 관심사 관리
 */
interface UserInterestRepository {
    fun findByUserId(userId: Long): List<InterestPath>
    fun save(userId: Long, interestPath: InterestPath)
    fun deleteByUserId(userId: Long)
}
