package com.haruUp.missionembedding.entity

import com.haruUp.global.common.BaseEntity
import io.hypersistence.utils.hibernate.type.array.ListArrayType
import jakarta.persistence.*
import org.hibernate.annotations.Type

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

    @Column(name = "is_activated")
    val isActivated: Boolean = true,

    /**
     * 미션 라벨 (그룹핑용)
     * 예: "영어 단어 20개 외우기" → "영어 단어 외우기"
     * 배치에서 LLM 또는 임베딩 유사도로 생성
     */
    @Column(name = "label_name", length = 100)
    var labelName: String? = null
) : BaseEntity()
