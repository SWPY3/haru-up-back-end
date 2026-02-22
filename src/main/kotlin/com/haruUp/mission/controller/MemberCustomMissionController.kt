package com.haruUp.mission.controller

import com.haruUp.global.common.ApiResponse
import com.haruUp.global.security.MemberPrincipal
import com.haruUp.mission.application.MemberCustomMissionService
import com.haruUp.mission.domain.CustomMissionCreateRequest
import com.haruUp.mission.domain.CustomMissionStatusChangeRequest
import com.haruUp.mission.domain.MemberCustomMissionDto
import com.haruUp.mission.domain.MissionStatus
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.slf4j.LoggerFactory
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@Tag(name = "사용자 작성 미션 API", description = "사용자가 직접 작성하는 커스텀 미션 관리")
@RestController
@RequestMapping("/api/member/custom-mission")
class MemberCustomMissionController(
    private val memberCustomMissionService: MemberCustomMissionService
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Operation(
        summary = "커스텀 미션 생성",
        description = """
            사용자가 직접 미션을 작성합니다.
            - 경험치가 부여되지 않습니다.
            - 하루 5개 미션 제한에 포함되지 않습니다.
            - 생성 즉시 ACTIVE 상태가 됩니다.

            **호출 예시:**
            ```json
            {
              "missionContent": "오늘 책 30분 읽기",
              "targetDate": "2026-02-22"
            }
            ```
        """
    )
    @PostMapping
    fun createMission(
        @AuthenticationPrincipal principal: MemberPrincipal,
        @RequestBody request: CustomMissionCreateRequest
    ): ResponseEntity<ApiResponse<MemberCustomMissionDto>> {
        val result = memberCustomMissionService.createMission(principal.id, request)
        return ResponseEntity.ok(ApiResponse.success(result))
    }

    @Operation(
        summary = "커스텀 미션 목록 조회",
        description = """
            사용자가 작성한 커스텀 미션 목록을 조회합니다.

            **호출 예시:**
            ```
            GET /api/member/custom-mission
            GET /api/member/custom-mission?targetDate=2026-02-22
            GET /api/member/custom-mission?missionStatus=ACTIVE
            GET /api/member/custom-mission?targetDate=2026-02-22&missionStatus=ACTIVE,COMPLETED
            ```
        """
    )
    @GetMapping
    fun getMissions(
        @AuthenticationPrincipal principal: MemberPrincipal,
        @Parameter(description = "조회할 날짜 (yyyy-MM-dd, 미입력시 전체 조회)", example = "2026-02-22")
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        targetDate: LocalDate?,
        @Parameter(description = "미션 상태 필터 (콤마로 구분, 미입력시 전체 조회)", example = "ACTIVE,COMPLETED")
        @RequestParam(required = false) missionStatus: String?
    ): ApiResponse<List<MemberCustomMissionDto>> {
        val statuses = missionStatus?.split(",")
            ?.map { it.trim().uppercase() }
            ?.mapNotNull {
                try { MissionStatus.valueOf(it) }
                catch (e: IllegalArgumentException) { null }
            }

        return ApiResponse.success(
            memberCustomMissionService.getMissions(principal.id, targetDate, statuses)
        )
    }

    @Operation(
        summary = "커스텀 미션 상태 변경",
        description = """
            커스텀 미션의 상태를 변경합니다.
            - 경험치가 부여되지 않습니다.

            **호출 예시:**
            ```json
            {
              "memberCustomMissionId": 1,
              "missionStatus": "COMPLETED"
            }
            ```
        """
    )
    @PutMapping("/status")
    fun changeStatus(
        @AuthenticationPrincipal principal: MemberPrincipal,
        @RequestBody request: CustomMissionStatusChangeRequest
    ): ResponseEntity<ApiResponse<MemberCustomMissionDto>> {
        val result = memberCustomMissionService.updateStatus(
            memberId = principal.id,
            missionId = request.memberCustomMissionId,
            status = request.missionStatus
        )
        return ResponseEntity.ok(ApiResponse.success(result))
    }

    @Operation(
        summary = "커스텀 미션 삭제",
        description = """
            커스텀 미션을 soft delete 합니다.

            **호출 예시:**
            ```
            DELETE /api/member/custom-mission/1
            ```
        """
    )
    @DeleteMapping("/{customMissionId}")
    fun deleteMission(
        @AuthenticationPrincipal principal: MemberPrincipal,
        @Parameter(description = "커스텀 미션 ID", required = true)
        @PathVariable customMissionId: Long
    ): ResponseEntity<ApiResponse<String>> {
        memberCustomMissionService.softDeleteMission(principal.id, customMissionId)
        return ResponseEntity.ok(ApiResponse.success("OK"))
    }
}
