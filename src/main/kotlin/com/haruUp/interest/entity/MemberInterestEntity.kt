package com.haruUp.interest.entity

import com.haruUp.global.common.BaseEntity
import io.hypersistence.utils.hibernate.type.array.ListArrayType
import jakarta.persistence.*
import org.hibernate.annotations.ColumnDefault
import org.hibernate.annotations.Type
import java.time.LocalDateTime

/**
 * 멤버-관심사 연결 Entity
 *
 * 사용자가 선택한 관심사를 저장
 */
@Entity
@Table(
    name = "member_interest",
    indexes = [
        Index(name = "idx_member_interest_member_id", columnList = "member_id"),
        Index(name = "idx_member_interest_interest_id", columnList = "interest_id"),
        Index(name = "idx_member_interest_created_at", columnList = "created_at")
    ]
)
class MemberInterestEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "member_id", nullable = false)
    val memberId: Long,

    @Column(name = "interest_id", nullable = false)
    var interestId: Long,

    /**
     * 직접 저장된 전체 경로 배열 (PostgreSQL TEXT[])
     * 예: ["외국어 공부", "일본어", "단어 학습"]
     */
    @Type(ListArrayType::class)
    @Column(name = "direct_full_path", columnDefinition = "text[]")
    var directFullPath: List<String>? = null,

    /**
     * 오늘의 미션 재추천 횟수
     * today-recommend API 호출 시마다 1씩 증가
     */
    @ColumnDefault("0")
    @Column(name = "reset_mission_count", nullable = false)
    var resetMissionCount: Int = 0
): BaseEntity() {
    fun incrementResetMissionCount() {
        this.resetMissionCount++
    }

    fun update(newInterestId: Long, newDirectFullPath: List<String>) {
        this.interestId = newInterestId
        this.directFullPath = newDirectFullPath
    }
}
