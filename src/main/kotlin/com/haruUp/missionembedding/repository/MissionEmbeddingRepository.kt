package com.haruUp.missionembedding.repository

import com.haruUp.missionembedding.entity.MissionEmbeddingEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

/**
 * 미션 임베딩 Repository
 */
@Repository
interface MissionEmbeddingRepository : JpaRepository<MissionEmbeddingEntity, Long> {
    /**
     * 미션 내용과 카테고리로 중복 체크
     */
    @Query(
        value = """
            SELECT * FROM mission_embeddings
            WHERE mission_content = :missionContent
            AND direct_full_path = CAST(:directFullPath AS TEXT[])
            LIMIT 1
        """,
        nativeQuery = true
    )
    fun findByMissionContentAndCategory(
        @Param("missionContent") missionContent: String,
        @Param("directFullPath") directFullPath: String  // PostgreSQL 배열 형식: "{대분류,중분류,소분류}"
    ): MissionEmbeddingEntity?

    /**
     * 벡터 유사도 검색 (코사인 유사도)
     *
     * @param embedding 검색할 임베딩 벡터
     * @param directFullPath 전체 경로 배열 (PostgreSQL 형식)
     * @param difficulty 난이도 (null이면 모든 난이도)
     * @param limit 반환할 결과 개수
     */
    @Query(
        value = """
            SELECT * FROM mission_embeddings
            WHERE is_activated = true
            AND direct_full_path = CAST(:directFullPath AS TEXT[])
            AND (:difficulty IS NULL OR difficulty = :difficulty OR difficulty IS NULL)
            AND embedding IS NOT NULL
            ORDER BY embedding <=> CAST(:embedding AS vector)
            LIMIT :limit
        """,
        nativeQuery = true
    )
    fun findByVectorSimilarity(
        @Param("embedding") embedding: String,
        @Param("directFullPath") directFullPath: String,  // PostgreSQL 배열 형식: "{대분류,중분류,소분류}"
        @Param("difficulty") difficulty: Int?,
        @Param("limit") limit: Int
    ): List<MissionEmbeddingEntity>

    /**
     * 난이도별 미션 1개씩 조회 (대분류 필터 + 임베딩 유사도 + usage_count 우선)
     *
     * 대분류(direct_full_path[1])로 먼저 필터링 후,
     * 임베딩 유사도로 관련 미션 검색, usage_count 높은 순 우선
     * 코사인 거리 임계값: 0.8 (0=동일, 1=직교, 2=반대)
     */
    @Query(
        value = """
            SELECT * FROM (
                SELECT *,
                       ROW_NUMBER() OVER (
                           PARTITION BY difficulty
                           ORDER BY usage_count DESC, embedding <=> CAST(:embedding AS vector)
                       ) as rn
                FROM mission_embeddings
                WHERE is_activated = true
                AND difficulty IN (1, 2, 3, 4, 5)
                AND embedding IS NOT NULL
                AND direct_full_path[1] = :majorCategory
                AND (embedding <=> CAST(:embedding AS vector)) < 0.8
            ) sub
            WHERE rn = 1
            ORDER BY difficulty
        """,
        nativeQuery = true
    )
    fun findOnePerDifficulty(
        @Param("embedding") embedding: String,
        @Param("majorCategory") majorCategory: String
    ): List<MissionEmbeddingEntity>

    /**
     * 미션 임베딩 저장 (vector 타입 캐스팅 포함)
     */
    @Modifying
    @Query(
        value = """
            INSERT INTO mission_embeddings
            (direct_full_path, difficulty, mission_content, embedding, usage_count, is_activated, created_at)
            VALUES (CAST(:directFullPath AS TEXT[]), :difficulty, :missionContent, CAST(:embedding AS vector), :usageCount, :isActivated, :createdAt)
        """,
        nativeQuery = true
    )
    fun insertMissionEmbedding(
        @Param("directFullPath") directFullPath: String,  // PostgreSQL 배열 형식: "{대분류,중분류,소분류}"
        @Param("difficulty") difficulty: Int?,
        @Param("missionContent") missionContent: String,
        @Param("embedding") embedding: String?,
        @Param("usageCount") usageCount: Int,
        @Param("isActivated") isActivated: Boolean,
        @Param("createdAt") createdAt: java.time.LocalDateTime
    )

    /**
     * 사용 횟수 증가 (UPDATE without touching embedding field)
     */
    @Modifying
    @Query(
        value = """
            UPDATE mission_embeddings
            SET usage_count = usage_count + 1,
                updated_at = :updatedAt
            WHERE id = :id
        """,
        nativeQuery = true
    )
    fun incrementUsageCount(
        @Param("id") id: Long,
        @Param("updatedAt") updatedAt: java.time.LocalDateTime
    )

    /**
     * 임베딩 벡터 업데이트 (미션 선택 시 호출)
     */
    @Modifying
    @Query(
        value = """
            UPDATE mission_embeddings
            SET embedding = CAST(:embedding AS vector),
                usage_count = usage_count + 1,
                updated_at = :updatedAt
            WHERE id = :id
        """,
        nativeQuery = true
    )
    fun updateEmbedding(
        @Param("id") id: Long,
        @Param("embedding") embedding: String,
        @Param("updatedAt") updatedAt: java.time.LocalDateTime
    )

    /**
     * 임베딩 유사도로 라벨이 있는 미션 검색 (코사인 거리)
     * 유사도 threshold 이내의 가장 유사한 미션 1개 반환
     */
    @Query(
        value = """
            SELECT * FROM mission_embeddings
            WHERE label_name IS NOT NULL
              AND embedding IS NOT NULL
              AND (embedding <=> CAST(:embedding AS vector)) < :threshold
            ORDER BY embedding <=> CAST(:embedding AS vector)
            LIMIT 1
        """,
        nativeQuery = true
    )
    fun findSimilarMissionWithLabel(
        @Param("embedding") embedding: String,
        @Param("threshold") threshold: Double
    ): MissionEmbeddingEntity?

    /**
     * 라벨 업데이트
     */
    @Modifying
    @Query(
        value = """
            UPDATE mission_embeddings
            SET label_name = :labelName,
                updated_at = :updatedAt
            WHERE id = :id
        """,
        nativeQuery = true
    )
    fun updateLabelName(
        @Param("id") id: Long,
        @Param("labelName") labelName: String,
        @Param("updatedAt") updatedAt: java.time.LocalDateTime
    )
}
