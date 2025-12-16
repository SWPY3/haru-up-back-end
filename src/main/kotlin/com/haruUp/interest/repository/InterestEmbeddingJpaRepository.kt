package com.haruUp.interest.repository

import com.haruUp.interest.entity.InterestEmbeddingEntity
import com.haruUp.interest.model.InterestLevel
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

/**
 * 관심사 임베딩 JPA Repository (pgvector)
 *
 * pgvector의 코사인 유사도 연산자 사용:
 * - <=> : 코사인 거리 (1 - 코사인 유사도)
 * - <#> : 음의 내적 (negative inner product)
 * - <-> : L2 거리 (유클리드 거리)
 */
@Repository
interface InterestEmbeddingJpaRepository : JpaRepository<InterestEmbeddingEntity, Long> {

    /**
     * 레벨로 조회
     *
     * Note: 네이티브 쿼리 사용 (p6spy + hypersistence-utils 호환성 문제 해결)
     */
    @Query(
        value = """
            SELECT * FROM interest_embeddings
            WHERE level = :level
        """,
        nativeQuery = true
    )
    fun findByLevel(@Param("level") level: String): List<InterestEmbeddingEntity>

    /**
     * 코사인 유사도 기반 검색 (특정 레벨)
     *
     * pgvector의 <=> 연산자 사용:
     * - 0에 가까울수록 유사함 (코사인 거리 = 1 - 코사인 유사도)
     * - ORDER BY로 가장 유사한 순서대로 정렬
     *
     * @param embedding 쿼리 벡터 "[0.1, 0.2, ...]" 형태
     * @param level 검색할 레벨
     * @param limit 반환할 최대 개수
     * @return 유사한 임베딩 엔티티 목록
     */
    @Query(
        value = """
            SELECT * FROM interest_embeddings
            WHERE level = :level
              AND is_activated = true
              AND embedding IS NOT NULL
            ORDER BY embedding <=> CAST(:embedding AS vector)
            LIMIT :limit
        """,
        nativeQuery = true
    )
    fun findSimilarByLevel(
        @Param("embedding") embedding: String,
        @Param("level") level: String,
        @Param("limit") limit: Int
    ): List<InterestEmbeddingEntity>

    /**
     * 코사인 유사도 기반 검색 (임계값 적용)
     *
     * 유사도 점수를 계산하여 임계값 이상만 반환
     *
     * @param embedding 쿼리 벡터
     * @param level 검색할 레벨
     * @param threshold 최소 유사도 (0.0 ~ 1.0, 높을수록 엄격)
     * @param limit 반환할 최대 개수
     * @return 유사한 임베딩 엔티티와 유사도 점수
     */
    @Query(
        value = """
            SELECT *,
                   1 - (embedding <=> CAST(:embedding AS vector)) AS similarity
            FROM interest_embeddings
            WHERE level = :level
              AND is_activated = true
              AND embedding IS NOT NULL
              AND 1 - (embedding <=> CAST(:embedding AS vector)) >= :threshold
            ORDER BY embedding <=> CAST(:embedding AS vector)
            LIMIT :limit
        """,
        nativeQuery = true
    )
    fun findSimilarWithScore(
        @Param("embedding") embedding: String,
        @Param("level") level: String,
        @Param("threshold") threshold: Double,
        @Param("limit") limit: Int
    ): List<InterestEmbeddingEntity>

    /**
     * 전체 임베딩 개수
     */
    @Query("SELECT COUNT(*) FROM InterestEmbeddingEntity")
    fun countAll(): Long

    /**
     * 레벨별 임베딩 개수
     */
    fun countByLevel(level: InterestLevel): Long

    /**
     * 하이브리드 스코어 기반 검색 (유사도 + 인기도)
     *
     * score = similarity * 0.7 + (usageCount / maxUsageCount) * 0.3
     *
     * @param embedding 쿼리 벡터
     * @param level 검색할 레벨
     * @param threshold 최소 유사도 (0.0 ~ 1.0)
     * @param limit 반환할 최대 개수
     * @return 하이브리드 점수로 정렬된 임베딩 엔티티 목록
     */
    @Query(
        value = """
            WITH max_usage AS (
                SELECT MAX(usage_count) as max_count
                FROM interest_embeddings
                WHERE level = :level
                  AND is_activated = true
                  AND embedding IS NOT NULL
            )
            SELECT ie.*,
                   (1 - (ie.embedding <=> CAST(:embedding AS vector))) * 0.7 +
                   (CASE WHEN max_usage.max_count > 0
                         THEN (ie.usage_count::float / max_usage.max_count) * 0.3
                         ELSE 0 END) AS hybrid_score
            FROM interest_embeddings ie, max_usage
            WHERE ie.level = :level
              AND ie.is_activated = true
              AND ie.embedding IS NOT NULL
              AND 1 - (ie.embedding <=> CAST(:embedding AS vector)) >= :threshold
            ORDER BY hybrid_score DESC
            LIMIT :limit
        """,
        nativeQuery = true
    )
    fun findSimilarWithHybridScore(
        @Param("embedding") embedding: String,
        @Param("level") level: String,
        @Param("threshold") threshold: Double,
        @Param("limit") limit: Int
    ): List<InterestEmbeddingEntity>

    /**
     * 임베딩 데이터 삽입 (Native Query)
     *
     * pgvector 타입으로 변환하여 삽입
     * fullPath는 PostgreSQL 배열 형식 (예: "{외국어 공부,일본어,단어 학습}")
     */
    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true)
    @Query(
        value = """
            INSERT INTO interest_embeddings (name, level, parent_id, full_path, embedding, usage_count, created_source, is_activated, created_at)
            VALUES (:name, :level, :parentId, CAST(:fullPath AS TEXT[]), CAST(:embedding AS vector), :usageCount, :createdSource, :isActivated, :createdAt)
        """,
        nativeQuery = true
    )
    fun insertEmbedding(
        @Param("name") name: String,
        @Param("level") level: String,
        @Param("parentId") parentId: String?,
        @Param("fullPath") fullPath: String,  // PostgreSQL 배열 형식: "{value1,value2,...}"
        @Param("embedding") embedding: String?,
        @Param("usageCount") usageCount: Int,
        @Param("createdSource") createdSource: String,
        @Param("isActivated") isActivated: Boolean,
        @Param("createdAt") createdAt: java.time.LocalDateTime
    )

    /**
     * full_path로 검색하여 usage_count 증가
     *
     * 대분류, 중분류, 소분류가 정확히 일치하는 row의 usage_count만 증가
     * fullPath는 PostgreSQL 배열 형식 (예: "{외국어 공부,일본어,단어 학습}")
     */
    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true)
    @Query(
        value = """
            UPDATE interest_embeddings
            SET usage_count = usage_count + 1,
                updated_at = :updatedAt
            WHERE full_path = CAST(:fullPath AS TEXT[])
        """,
        nativeQuery = true
    )
    fun incrementUsageCountByFullPath(
        @Param("fullPath") fullPath: String,  // PostgreSQL 배열 형식: "{value1,value2,...}"
        @Param("updatedAt") updatedAt: java.time.LocalDateTime
    ): Int  // 업데이트된 행 개수 반환

    /**
     * created_source로 필터링하여 조회
     *
     * 특정 created_source 값을 가진 임베딩만 조회
     * (AI, SYSTEM, USER 등으로 필터링 가능)
     */
    @Query("SELECT e FROM InterestEmbeddingEntity e WHERE e.createdSource = :createdSource")
    fun findByCreatedSource(@Param("createdSource") createdSource: String): List<InterestEmbeddingEntity>

    /**
     * created_source와 level로 필터링하여 조회
     */
    @Query("SELECT e FROM InterestEmbeddingEntity e WHERE e.createdSource = :createdSource AND CAST(e.level AS string) = :level")
    fun findByCreatedSourceAndLevel(
        @Param("createdSource") createdSource: String,
        @Param("level") level: String
    ): List<InterestEmbeddingEntity>

    /**
     * full_path로 관심사 ID 조회 (PostgreSQL 배열 비교)
     *
     * @param fullPath 관심사 경로 배열 형식 (예: "{운동,헬스,근력 키우기}")
     * @return 해당 경로의 관심사 ID (없으면 null)
     */
    @Query(
        value = """
            SELECT id FROM interest_embeddings
            WHERE full_path::text[] = CAST(:fullPath AS TEXT[])
            LIMIT 1
        """,
        nativeQuery = true
    )
    fun findIdByFullPath(@Param("fullPath") fullPath: String): Long?

    /**
     * created_source와 isActivated로 필터링하여 조회
     */
    @Query("SELECT e FROM InterestEmbeddingEntity e WHERE e.createdSource = :createdSource AND e.isActivated = :isActivated")
    fun findByCreatedSourceAndIsActivated(
        @Param("createdSource") createdSource: String,
        @Param("isActivated") isActivated: Boolean
    ): List<InterestEmbeddingEntity>

    /**
     * 레벨과 활성화 여부로 조회
     */
    @Query("SELECT e FROM InterestEmbeddingEntity e WHERE CAST(e.level AS string) = :level AND e.isActivated = :isActivated")
    fun findByLevelAndIsActivated(
        @Param("level") level: String,
        @Param("isActivated") isActivated: Boolean
    ): List<InterestEmbeddingEntity>

    /**
     * 이름과 레벨로 조회 (활성화된 것만)
     */
    @Query("SELECT e FROM InterestEmbeddingEntity e WHERE e.name = :name AND CAST(e.level AS string) = :level AND e.isActivated = :isActivated")
    fun findByNameAndLevelAndIsActivated(
        @Param("name") name: String,
        @Param("level") level: String,
        @Param("isActivated") isActivated: Boolean
    ): InterestEmbeddingEntity?

    /**
     * 인기도 순으로 조회 (usage_count 내림차순)
     */
    @Query(
        value = """
            SELECT * FROM interest_embeddings
            WHERE level = :level
              AND is_activated = true
            ORDER BY usage_count DESC
            LIMIT :limit
        """,
        nativeQuery = true
    )
    fun findPopularByLevel(
        @Param("level") level: String,
        @Param("limit") limit: Int
    ): List<InterestEmbeddingEntity>

    /**
     * createdSource, parentId로 필터링하여 조회 (활성화된 것만)
     * parentId가 특정 값인 경우 사용
     */
    @Query("SELECT e FROM InterestEmbeddingEntity e WHERE e.createdSource = :createdSource AND e.parentId = :parentId AND e.isActivated = :isActivated")
    fun findByCreatedSourceAndParentIdAndIsActivated(
        @Param("createdSource") createdSource: String,
        @Param("parentId") parentId: String,
        @Param("isActivated") isActivated: Boolean
    ): List<InterestEmbeddingEntity>

    /**
     * createdSource로 필터링하고 parentId가 NULL인 것만 조회 (활성화된 것만)
     * 대분류(MAIN) 조회 시 사용
     */
    @Query("SELECT e FROM InterestEmbeddingEntity e WHERE e.createdSource = :createdSource AND e.parentId IS NULL AND e.isActivated = :isActivated")
    fun findByCreatedSourceAndParentIdIsNullAndIsActivated(
        @Param("createdSource") createdSource: String,
        @Param("isActivated") isActivated: Boolean
    ): List<InterestEmbeddingEntity>
}
