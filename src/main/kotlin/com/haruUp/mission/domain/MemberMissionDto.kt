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

    var expEarned : Int,

    var postponedAt : LocalDate? = null,

    var targetDate: LocalDate = LocalDate.now()

) : BaseEntity() {

    fun toEntity() : MemberMission = MemberMission(
        id = this.id,
        memberId = this.memberId,
        missionId = this.missionId,
        memberInterestId = this.memberInterestId,
        expEarned = this.expEarned,
        missionStatus = this.missionStatus,
        postponedAt = this.postponedAt,
        targetDate = this.targetDate
    )
}

data class AiMissionResult(
    val missionId: Long,
    val score: Double,
    val reason: String
)

data class MissionCandidateDto(
    val memberMissionId: Long,
    val missionStatus: MissionStatus,
    val content: String,
    val directFullPath: List<String>,  // 전체 경로 배열 ["대분류", "중분류", "소분류"]
    val difficulty: Int?,
    val targetDate: LocalDate,
    val reason: String
)

data class MissionRecommendResult(
    val missions: List<MissionCandidateDto>
)

/**
 * 개별 미션 상태 변경 항목
 */
data class MissionStatusChangeItem(
    val id: Long,
    val missionStatus: MissionStatus? = null,
    val postponedAt: LocalDate? = null
)

/**
 * 미션 상태 변경 벌크 요청 DTO
 */
data class MissionStatusChangeRequest(
    val missions: List<MissionStatusChangeItem>
)