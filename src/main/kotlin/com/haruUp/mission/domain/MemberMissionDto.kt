package com.haruUp.mission.domain

import com.haruUp.global.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import java.time.LocalDate

class MemberMissionDto (

    val id: Long? = null,

    val memberId: Long,

    val missionId: Long,

    var isCompleted: Boolean = false,

    var missionStatus : MissionStatus = MissionStatus.ACTIVE,

    var expEarned : Int,

    var targetDate : LocalDate = LocalDate.now()

) : BaseEntity() {

    fun toEntity() : MemberMission = MemberMission(
        id = this.id,
        memberId = this.memberId,
        missionId = this.missionId,
        isCompleted = this.isCompleted,
        expEarned = this.expEarned,
        missionStatus = this.missionStatus,
        targetDate = this.targetDate
    )
}