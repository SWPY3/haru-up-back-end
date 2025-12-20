package com.haruUp.mission.domain

import com.haruUp.global.common.BaseEntity
import java.time.LocalDate
import java.time.LocalDateTime

class MemberMissionDto (

    var id: Long? = null,

    var memberId: Long,

    var missionId: Long,

    var memberInterestId: Long,

    var missionStatus : MissionStatus = MissionStatus.ACTIVE,

    var missionLevel : Int = 0,

    var expEarned : Int,

    var targetDate : LocalDate = LocalDate.now()

) : BaseEntity() {

    fun toEntity() : MemberMission = MemberMission(
        id = this.id,
        memberId = this.memberId,
        missionId = this.missionId,
        memberInterestId = this.memberInterestId,
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
    val directFullPath: List<String>,  // 전체 경로 배열 ["대분류", "중분류", "소분류"]
    val difficulty: Int?,
    val reason: String
)

data class MissionRecommendResult(
    val generatedAt: LocalDateTime,
    val missions: List<MissionCandidateDto>
)