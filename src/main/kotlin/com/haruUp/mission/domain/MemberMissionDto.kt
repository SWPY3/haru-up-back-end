package com.haruUp.mission.domain

import com.haruUp.global.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import java.time.LocalDate
import java.time.LocalDateTime

class MemberMissionDto (

    var id: Long? = null,

    var memberId: Long,

    var missionId: Long,

    var isCompleted: Boolean = false,

    var missionStatus : MissionStatus = MissionStatus.ACTIVE,

    var missionLevel : Int = 0,

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
        targetDate = this.targetDate,
        missionLevel = this.missionLevel
    )
}

data class AiMissionResult(
    val missionId: Long,
    val score: Double,
    val reason: String
)

data class MissionCandidateDto(
    val embeddingMissionId: Long,
    val content: String,
    val mainCategory: String,
    val middleCategory: String?,
    val subCategory: String?,
    val difficulty: Int?,
    val reason: String
)

data class MissionRecommendResult(
    val generatedAt: LocalDateTime,
    val missions: List<MissionCandidateDto>
)