package com.haruUp.interest.entity

import jakarta.persistence.*
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
    val interestId: Long,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
)
