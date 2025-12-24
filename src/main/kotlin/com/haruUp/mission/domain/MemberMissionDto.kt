package com.haruUp.mission.domain

import com.haruUp.global.common.BaseEntity
import com.haruUp.missionembedding.dto.SelectedMissionDto
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate
import java.time.LocalDateTime

class MemberMissionDto (

    var id: Long? = null,

    var memberId: Long,

    var missionId: Long,

    var memberInterestId: Long,

    var missionStatus : MissionStatus = MissionStatus.ACTIVE,

    var expEarned : Int,

    var targetDate: LocalDate = LocalDate.now()

) : BaseEntity() {

    fun toEntity() : MemberMission = MemberMission(
        id = this.id,
        memberId = this.memberId,
        missionId = this.missionId,
        memberInterestId = this.memberInterestId,
        expEarned = this.expEarned,
        missionStatus = this.missionStatus,
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
    @Schema(
        description = "member_mission ID",
        example = "1",
        required = true
    )
    val memberMissionId: Long,

    @Schema(
        description = "member_mission ID",
        example = "COMPLETED",
        required = false
    )
    val missionStatus: MissionStatus? = null
)

/**
 * 미션 상태 변경 벌크 요청 DTO
 */
data class MissionStatusChangeRequest(
    val missions: List<MissionStatusChangeItem>
)

/**
 * 미션 선택 요청
 */
@Schema(description = "미션 선택 요청")
data class MemberMissionSelectionRequest(
    @Schema(
        description = "선택할 member_mission ID 목록",
        example = "[1, 2, 3]",
        required = true
    )
    val memberMissionIds: List<Long>
)