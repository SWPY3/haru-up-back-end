package com.haruUp.mission.controller

import com.haruUp.global.common.ApiResponse
import com.haruUp.global.security.MemberPrincipal
import com.haruUp.global.ratelimit.RateLimit
import com.haruUp.mission.application.MemberMissionUseCase
import com.haruUp.mission.application.MissionRecommendUseCase
import com.haruUp.mission.domain.MemberMissionDto
import com.haruUp.mission.domain.MissionRecommendResult
import com.haruUp.mission.domain.MissionStatusChangeRequest
import com.haruUp.missionembedding.dto.TodayMissionRecommendationRequest
import com.haruUp.missionembedding.dto.TodayMissionRetryRequest
import com.haruUp.missionembedding.dto.MissionRecommendationResponse
import com.haruUp.mission.domain.MemberMissionSelectionRequest
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "멤버 미션 API", description = "멤버 미션 관리 및 추천")
@RestController
@RequestMapping("/api/member/mission")
class MemberMissionController(
    private val memberMissionUseCase: MemberMissionUseCase,
    private val missionRecommendUseCase: MissionRecommendUseCase
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 오늘의 미션 목록 조회
     */
    @GetMapping
    fun todayMissions(
        @AuthenticationPrincipal principal: MemberPrincipal
    ): ApiResponse<List<MemberMissionDto>> {

        return ApiResponse.success(
            memberMissionUseCase.missionTodayList(principal.id)
        )
    }

    /**
     * 미션 상태 벌크 변경 (ACTIVE / COMPLETED / INACTIVE / POSTPONED) 및 미루기
     */
    @Operation(
        summary = "미션 상태 벌크 변경",
        description = """
            여러 미션의 상태를 한 번에 변경합니다.

            **상태 변경:**
            - missionStatus: ACTIVE (선택), COMPLETED (완료), INACTIVE (포기), POSTPONED (내일로 미루기)

            **호출 예시:**
            ```json
            {
              "missions": [
                { "id": 1, "missionStatus": "COMPLETED" },
                { "id": 2, "missionStatus": "ACTIVE" },
                { "id": 3, "missionStatus": "POSTPONED" }
              ]
            }
            ```
        """
    )
    @PostMapping("/status")
    fun changeMissionStatus(
        @AuthenticationPrincipal principal: MemberPrincipal,
        @RequestBody request: MissionStatusChangeRequest
    ): ApiResponse<Any> {

        return try {
            memberMissionUseCase.missionChangeStatus(request)
            ApiResponse.success("OK")
        } catch (e: IllegalArgumentException) {
            ApiResponse(success = false, data = null, errorMessage = e.message ?: "잘못된 요청입니다.")
        } catch (e: IllegalStateException) {
            ApiResponse(success = false, data = null, errorMessage = e.message ?: "처리할 수 없는 상태입니다.")
        } catch (e: Exception) {
            logger.error("미션 상태 변경 실패: ${e.message}", e)
            ApiResponse(success = false, data = null, errorMessage = "서버 오류가 발생했습니다.")
        }
    }

    /**
     * 미션 선택 API
     *
     * 사용자가 선택한 미션들을 데이터베이스에 저장
     *
     * @param request 미션 선택 요청
     * @return 저장 결과
     */
    @Operation(
        summary = "미션 선택",
        description = """
            사용자가 선택한 미션들을 저장합니다.

            **호출 예시:**
            ```json
            {
              "missions": [
                {
                  "memberInterestId": 2,
                  "missionId": 3
                }
              ]
            }
            ```

            **필드 설명:**
            - memberInterestId: 멤버 관심사 ID (반드시 소분류까지 입력된 memberInterestId로 입력해주세요.)
            - missionId: 미션 번호
        """
    )
    @PostMapping("/select")
    fun selectMissions(
        @AuthenticationPrincipal principal: MemberPrincipal,
        @Parameter(
            description = "미션 선택 요청 정보",
            required = true,
            schema = Schema(implementation = MemberMissionSelectionRequest::class)
        )
        @RequestBody request: MemberMissionSelectionRequest
    ): ResponseEntity<ApiResponse<List<Long>>> {
        logger.info("미션 선택 요청 - 사용자: ${principal.id}, 미션 개수: ${request.missions.size}")

        return try {
            val savedMissionIds = missionRecommendUseCase.memberMissionSelection(principal.id, request)
            logger.info("미션 선택 완료 - 저장된 개수: ${savedMissionIds.size}")
            ResponseEntity.ok(ApiResponse.success(savedMissionIds))
        } catch (e: IllegalArgumentException) {
            logger.error("잘못된 요청: ${e.message}")
            ResponseEntity.badRequest().body(
                ApiResponse(
                    success = false,
                    data = emptyList(),
                    errorMessage = e.message ?: "유효성 검증 실패"
                )
            )
        } catch (e: Exception) {
            logger.error("미션 선택 실패: ${e.message}", e)
            ResponseEntity.internalServerError().body(
                ApiResponse(
                    success = false,
                    data = emptyList(),
                    errorMessage = "서버 오류가 발생했습니다"
                )
            )
        }
    }

    /**
     * 오늘의 미션 추천 API
     *
     * @param request 오늘의 미션 추천 요청 (memberInterestId)
     * @return 추천된 미션 목록
     */
    @Operation(
        summary = "오늘의 미션 추천",
        description = """
            오늘의 미션 추천 정보를 조회합니다.

            **호출 예시:**
            ```json
            {
              "memberInterestId": 1
            }
            ```
        """
    )
    @PostMapping("/recommend")
    fun recommendMissions(
        @AuthenticationPrincipal principal: MemberPrincipal,
        @Parameter(
            description = "오늘의 미션 추천 요청 정보",
            required = true,
            schema = Schema(implementation = TodayMissionRecommendationRequest::class)
        )
        @RequestBody request: TodayMissionRecommendationRequest
    ): ResponseEntity<ApiResponse<MissionRecommendResult>> = runBlocking {
        try {
            val response = missionRecommendUseCase.recommendToday(
                memberId = principal.id,
                memberInterestId = request.memberInterestId
            )
            ResponseEntity.ok(ApiResponse.success(response))
        } catch (e: IllegalArgumentException) {
            logger.error("잘못된 요청: ${e.message}")
            ResponseEntity.badRequest().body(
                ApiResponse(
                    success = false,
                    data = null,
                    errorMessage = e.message ?: "잘못된 요청입니다."
                )
            )
        } catch (e: Exception) {
            logger.error("오늘의 미션 재추천 실패: ${e.message}", e)
            ResponseEntity.internalServerError().body(
                ApiResponse(
                    success = false,
                    data = null,
                    errorMessage = "서버 오류가 발생했습니다."
                )
            )
        }
    }

    /**
     * 오늘의 미션 재추천 API
     *
     * 사용자 프로필과 관심사 정보를 기반으로 미션 재추천
     * - ACTIVE 상태인 기존 미션 자동 제외
     * - Redis에 저장된 이전 추천 미션 자동 제외
     * - 추천된 미션 ID는 Redis에 캐싱 (24시간)
     * - reset_mission_count 카운트 증가
     *
     * @param request 오늘의 미션 재추천 요청 (memberInterestId)
     * @return 추천된 미션 목록
     */
    @Operation(
        summary = "오늘의 미션 재추천",
        description = """
            사용자 프로필과 선택한 관심사를 기반으로 오늘의 미션을 재추천합니다.

            **자동 제외 기능:**
            - ACTIVE 상태인 기존 미션 자동 제외
            - Redis에 캐싱된 이전 추천 미션 자동 제외 (24시간)

            **호출 예시:**
            ```json
            {
              "memberInterestId": 1
            }
            ```
        """
    )
    @RateLimit(key = "api:member:mission:retry", limit = 50)
    @PostMapping("/retry")
    fun retryMissions(
        @AuthenticationPrincipal principal: MemberPrincipal,
        @Parameter(
            description = "오늘의 미션 재추천 요청 정보",
            required = true,
            schema = Schema(implementation = TodayMissionRetryRequest::class)
        )
        @RequestBody request: TodayMissionRetryRequest
    ): ResponseEntity<ApiResponse<MissionRecommendationResponse>> = runBlocking {
        try {
            val response = missionRecommendUseCase.retryRecommend(
                memberId = principal.id,
                memberInterestId = request.memberInterestId,
                excludeMemberMissionIds = request.excludeMemberMissionIds
            )
            ResponseEntity.ok(ApiResponse.success(response))
        } catch (e: IllegalArgumentException) {
            logger.error("잘못된 요청: ${e.message}")
            ResponseEntity.badRequest().body(
                ApiResponse(
                    success = false,
                    data = null,
                    errorMessage = e.message ?: "잘못된 요청입니다."
                )
            )
        } catch (e: Exception) {
            logger.error("오늘의 미션 재추천 실패: ${e.message}", e)
            ResponseEntity.internalServerError().body(
                ApiResponse(
                    success = false,
                    data = null,
                    errorMessage = "서버 오류가 발생했습니다."
                )
            )
        }
    }
}