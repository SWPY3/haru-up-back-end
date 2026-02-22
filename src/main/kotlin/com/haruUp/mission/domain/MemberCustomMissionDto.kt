package com.haruUp.mission.domain

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate

@Schema(description = "사용자 작성 미션 정보")
data class MemberCustomMissionDto(
    @Schema(description = "커스텀 미션 ID", example = "1")
    val id: Long? = null,

    @Schema(description = "멤버 ID", example = "1")
    val memberId: Long,

    @Schema(description = "미션 내용", example = "오늘 책 30분 읽기")
    val missionContent: String,

    @Schema(description = "미션 상태", example = "ACTIVE")
    val missionStatus: MissionStatus = MissionStatus.ACTIVE,

    @Schema(description = "목표 날짜", example = "2026-02-22")
    val targetDate: LocalDate = LocalDate.now()
)

@Schema(description = "사용자 작성 미션 생성 요청")
data class CustomMissionCreateRequest(
    @Schema(description = "미션 내용", example = "오늘 책 30분 읽기", required = true)
    val missionContent: String,

    @Schema(description = "목표 날짜 (미입력시 오늘)", example = "2026-02-22")
    val targetDate: LocalDate? = null
)

@Schema(description = "사용자 작성 미션 상태 변경 요청")
data class CustomMissionStatusChangeRequest(
    @Schema(description = "커스텀 미션 ID", example = "1", required = true)
    val memberCustomMissionId: Long,

    @Schema(description = "변경할 미션 상태", example = "COMPLETED", required = true)
    val missionStatus: MissionStatus
)
