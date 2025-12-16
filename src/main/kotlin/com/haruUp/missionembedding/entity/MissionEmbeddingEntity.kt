package com.haruUp.missionembedding.entity

import io.hypersistence.utils.hibernate.type.array.ListArrayType
import jakarta.persistence.*
import org.hibernate.annotations.Type
import java.time.LocalDateTime

/**
 * 미션 임베딩 Entity (pgvector)
 *
 * 사용자가 선택한 미션이나 AI가 생성한 미션을 임베딩하여 저장
 * 향후 유사한 미션 추천에 활용
 *
 * 추가 인덱스 (schema.sql에서 생성):
 * - idx_mission_direct_full_path_gin: GIN 인덱스 (direct_full_path 배열 검색 최적화)
 * - idx_mission_content_path: 복합 인덱스 (mission_content + direct_full_path)
 */
@Entity
@Table(
    name = "mission_embeddings",
    indexes = [
        Index(name = "idx_mission_is_activated", columnList = "is_activated"),
        Index(name = "idx_mission_created_at", columnList = "created_at"),
        Index(name = "idx_mission_direct_full_path", columnList = "direct_full_path"),
        Index(name = "idx_mission_difficulty", columnList = "difficulty")
    ]
)
class MissionEmbeddingEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    /**
     * 관심사 전체 경로 배열 (예: ["외국어 공부", "영어", "단어 학습"])
     * PostgreSQL TEXT[] 타입 - hypersistence-utils로 List<String> 직접 매핑
     */
    @Type(ListArrayType::class)
    @Column(name = "direct_full_path", columnDefinition = "TEXT[]")
    val directFullPath: List<String> = emptyList(),

    @Column(name = "difficulty")
    val difficulty: Int? = null,

    @Column(name = "mission_content", nullable = false, columnDefinition = "TEXT")
    val missionContent: String,

    /**
     * 임베딩 벡터 (pgvector type)
     * Clova Embedding: 1024차원
     */
    @Column(name = "embedding", columnDefinition = "vector(1024)")
    val embedding: String? = null,  // "[0.1, 0.2, ...]" 형태의 문자열

    @Column(name = "usage_count")
    var usageCount: Int = 0,  // 이 미션이 선택된 횟수

    @Column(name = "is_activated")
    val isActivated: Boolean = true,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at")
    var updatedAt: LocalDateTime? = null
) {
    /**
     * 관심사 경로 문자열 반환 (표시용)
     */
    fun getInterestPath(): String {
        return directFullPath.joinToString(" > ")
    }

    /**
     * 임베딩 벡터를 Float 리스트로 변환
     */
    fun getEmbeddingVector(): List<Float>? {
        return embedding?.let {
            it.trim('[', ']')
                .split(',')
                .map { value -> value.trim().toFloat() }
        }
    }

    /**
     * 사용 횟수 증가
     */
    fun incrementUsageCount() {
        usageCount++
        updatedAt = LocalDateTime.now()
    }

    companion object {
        /**
         * Float 리스트를 pgvector 문자열로 변환
         */
        fun vectorToString(vector: List<Float>): String {
            return vector.joinToString(separator = ",", prefix = "[", postfix = "]")
        }

        /**
         * 카테고리 경로 리스트를 표시용 문자열로 변환
         * ["대분류", "중분류", "소분류"] → "대분류 > 중분류 > 소분류"
         */
        fun categoryPathToString(categoryPath: List<String>): String {
            return categoryPath.filter { it.isNotBlank() }.joinToString(" > ")
        }
    }
}
