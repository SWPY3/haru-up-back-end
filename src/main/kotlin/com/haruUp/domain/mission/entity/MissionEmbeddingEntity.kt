package com.haruUp.domain.mission.entity

import jakarta.persistence.*
import java.time.LocalDateTime

/**
 * 미션 임베딩 Entity (pgvector)
 *
 * 사용자가 선택한 미션이나 AI가 생성한 미션을 임베딩하여 저장
 * 향후 유사한 미션 추천에 활용
 */
@Entity
@Table(
    name = "mission_embeddings",
    indexes = [
        Index(name = "idx_mission_is_activated", columnList = "is_activated"),
        Index(name = "idx_mission_created_at", columnList = "created_at"),
        Index(name = "idx_mission_main_category", columnList = "main_category"),
        Index(name = "idx_mission_middle_category", columnList = "middle_category"),
        Index(name = "idx_mission_sub_category", columnList = "sub_category")
    ]
)
class MissionEmbeddingEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "main_category", nullable = false)
    val mainCategory: String,

    @Column(name = "middle_category")
    val middleCategory: String? = null,

    @Column(name = "sub_category")
    val subCategory: String? = null,

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
        return listOfNotNull(
            mainCategory,
            middleCategory?.takeIf { it.isNotBlank() },
            subCategory?.takeIf { it.isNotBlank() }
        ).joinToString(" > ")
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
         * 카테고리로 관심사 경로 문자열 생성
         */
        fun getCategoryPath(
            mainCategory: String,
            middleCategory: String?,
            subCategory: String?
        ): String {
            return listOfNotNull(
                mainCategory,
                middleCategory?.takeIf { it.isNotBlank() },
                subCategory?.takeIf { it.isNotBlank() }
            ).joinToString(" > ")
        }
    }
}
