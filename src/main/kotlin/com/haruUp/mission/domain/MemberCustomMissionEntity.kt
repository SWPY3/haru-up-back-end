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

@Entity
@NoArgsConstructor
@Table(
    name = "member_custom_mission",
    indexes = [
        Index(name = "idx_custom_mission_member_id", columnList = "member_id"),
        Index(name = "idx_custom_mission_target_date", columnList = "target_date")
    ]
)
class MemberCustomMissionEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "member_id", nullable = false)
    val memberId: Long,

    @Column(name = "mission_content", nullable = false, columnDefinition = "TEXT")
    var missionContent: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    val type: CustomMissionType = CustomMissionType.CUSTOM,

    @Enumerated(EnumType.STRING)
    var missionStatus: MissionStatus = MissionStatus.ACTIVE,

    @Column(name = "target_date", nullable = false)
    var targetDate: LocalDate = LocalDate.now()

) : BaseEntity() {

    fun toDto(): MemberCustomMissionDto = MemberCustomMissionDto(
        id = this.id,
        memberId = this.memberId,
        missionContent = this.missionContent,
        type = this.type,
        missionStatus = this.missionStatus,
        targetDate = this.targetDate
    )
}
