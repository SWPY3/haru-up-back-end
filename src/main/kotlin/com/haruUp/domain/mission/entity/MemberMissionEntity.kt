package com.haruUp.domain.mission.entity

import jakarta.persistence.*
import java.time.LocalDateTime

/**
 * 사용자-미션 연결 Entity
 *
 * 사용자가 선택한 미션을 저장
 */
@Entity
@Table(
    name = "member_mission",
    indexes = [
        Index(name = "idx_member_mission_member_id", columnList = "member_id"),
        Index(name = "idx_member_mission_mission_id", columnList = "mission_id"),
        Index(name = "idx_member_mission_created_at", columnList = "created_at")
    ]
)
class MemberMissionEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "member_id", nullable = false)
    val memberId: Long,

    @Column(name = "mission_id", nullable = false)
    val missionId: Long,

    @Column(name = "is_completed", nullable = false)
    var isCompleted: Boolean = false,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
)
