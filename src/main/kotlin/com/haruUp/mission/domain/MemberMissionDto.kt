package com.haruUp.mission.domain

import com.haruUp.global.common.BaseEntity
import jakarta.persistence.Column

class MemberMissionDto (

    val id: Long? = null,

    val memberId: Long,

    val missionId: Long,

    var isCompleted: Boolean = false,

    var expEarned : Int

) : BaseEntity() {

    fun toEntity() : MemberMission = MemberMission(
        id = this.id,
        memberId = this.memberId,
        missionId = this.missionId,
        isCompleted = this.isCompleted,
        expEarned = this.expEarned
    )
}