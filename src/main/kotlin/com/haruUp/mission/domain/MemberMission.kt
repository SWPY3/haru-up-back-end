package com.haruUp.mission.domain

import com.haruUp.global.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import lombok.NoArgsConstructor
import java.time.LocalDate
import java.time.LocalDateTime

@Entity
@NoArgsConstructor
@Table(
    indexes = [
        Index(name = "idx_member_mission_member_id", columnList = "member_id"),
        Index(name = "idx_member_mission_mission_id", columnList = "mission_id"),
        Index(name = "idx_member_mission_created_at", columnList = "created_at")
    ]
)
class MemberMission (
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "member_id", nullable = false)
    val memberId: Long,

    @Column(name = "mission_id", nullable = false)
    val missionId: Long,

    @Column(name = "member_interest_id", nullable = false)
    val memberInterestId: Long,

    @Column(name = "is_completed", nullable = false)
    var isCompleted: Boolean = false,

    @Enumerated(EnumType.STRING)
    var missionStatus : MissionStatus = MissionStatus.READY,

    var missionLevel : Int = 0,

    var expEarned : Int,

    var targetDate : LocalDate = LocalDate.now()

) : BaseEntity() {

    fun toDto() : MemberMissionDto = MemberMissionDto(
        id = this.id,
        memberId = this.memberId,
        missionId = this.missionId,
        memberInterestId = this.memberInterestId,
        isCompleted = this.isCompleted,
        expEarned = this.expEarned,
        missionStatus = this.missionStatus,
        targetDate = this.targetDate,
        missionLevel = this.missionLevel
    )

}