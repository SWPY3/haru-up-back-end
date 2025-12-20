package com.haruUp.mission.controller

import com.haruUp.global.common.ApiResponse
import com.haruUp.global.security.MemberPrincipal
import com.haruUp.global.clova.MissionMemberProfile
import com.haruUp.global.ratelimit.RateLimit
import com.haruUp.mission.application.MemberMissionUseCase
import com.haruUp.mission.domain.MemberMissionDto
import com.haruUp.mission.domain.MissionStatus
import com.haruUp.mission.domain.MissionStatusChangeRequest
import com.haruUp.mission.infrastructure.MemberMissionRepository
import com.haruUp.missionembedding.dto.TodayMissionRecommendationRequest
import com.haruUp.missionembedding.dto.MissionRecommendationResponse
import com.haruUp.missionembedding.service.MissionRecommendationService
import com.haruUp.missionembedding.service.TodayMissionCacheService
import com.haruUp.member.infrastructure.MemberProfileRepository
import com.haruUp.category.repository.JobRepository
import com.haruUp.category.repository.JobDetailRepository
import com.haruUp.interest.repository.MemberInterestJpaRepository
import com.haruUp.interest.model.InterestPath
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
import java.time.LocalDateTime
import java.time.Period

@Tag(name = "멤버 미션 API", description = "멤버 미션 관리 및 추천")
@RestController
@RequestMapping("/api/member/mission")
class MemberMissionController(
    private val memberMissionUseCase: MemberMissionUseCase,
    private val missionRecommendationService: MissionRecommendationService,
    private val todayMissionCacheService: TodayMissionCacheService,
    private val memberProfileRepository: MemberProfileRepository,
    private val memberMissionRepository: MemberMissionRepository,
    private val jobRepository: JobRepository,
    private val jobDetailRepository: JobDetailRepository,
    private val memberInterestRepository: MemberInterestJpaRepository
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
     * 미션 상태 벌크 변경 (ACTIVE / COMPLETED / INACTIVE) 및 미루기 (postponedAt 설정)
     */
    @Operation(
        summary = "미션 상태 벌크 변경",
        description = """
            여러 미션의 상태를 한 번에 변경합니다.

            **상태 변경:**
            - missionStatus: ACTIVE (선택), COMPLETED (완료), INACTIVE (포기)

            **미루기:**
            - postponedAt: 날짜를 설정하면 해당 날짜로 미루기 처리 (status는 변경되지 않음)

            **호출 예시:**
            ```json
            {
              "missions": [
                { "id": 1, "missionStatus": "COMPLETED" },
                { "id": 2, "missionStatus": "ACTIVE" },
                { "id": 3, "postponedAt": "2024-01-02" }
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
     * 오늘의 미션 추천 API
     *
     * 사용자 프로필(직업, 직업상세, 성별, 나이)과 관심사 정보를 기반으로 미션 추천
     * - ACTIVE 상태인 기존 미션 자동 제외
     * - Redis에 저장된 이전 추천 미션 자동 제외
     * - 추천된 미션은 mission_embeddings에 저장 (embedding=null)
     * - 추천된 미션 ID는 Redis에 캐싱 (24시간)
     * - reset_mission_count 카운트 증가
     *
     * @param request 오늘의 미션 추천 요청 (memberInterestId)
     * @return 추천된 미션 목록
     */
    @Operation(
        summary = "오늘의 미션 추천",
        description = """
            사용자 프로필과 선택한 관심사를 기반으로 오늘의 미션을 추천합니다.

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
    @RateLimit(key = "api:member:mission:recommend", limit = 50)
    @PostMapping("/recommend")
    fun recommendMissions(
        @AuthenticationPrincipal principal: MemberPrincipal,
        @Parameter(
            description = "오늘의 미션 추천 요청 정보",
            required = true,
            schema = Schema(implementation = TodayMissionRecommendationRequest::class)
        )
        @RequestBody request: TodayMissionRecommendationRequest
    ): ResponseEntity<ApiResponse<MissionRecommendationResponse>> = runBlocking {
        logger.info("오늘의 미션 추천 요청 - 사용자: ${principal.id}, memberInterestId: ${request.memberInterestId}")

        try {
            // 1. DB에서 사용자 프로필 조회
            val memberProfileEntity = memberProfileRepository.findByMemberId(principal.id)
                ?: return@runBlocking ResponseEntity.badRequest().body(
                    ApiResponse<MissionRecommendationResponse>(
                        success = false,
                        data = null,
                        errorMessage = "사용자 프로필을 찾을 수 없습니다."
                    )
                ).also {
                    logger.error("사용자 프로필을 찾을 수 없음: ${principal.id}")
                }

            // 2. 직업 정보 조회
            val jobName = memberProfileEntity.jobId?.let { jobId ->
                jobRepository.findById(jobId).orElse(null)?.jobName
            }
            val jobDetailName = memberProfileEntity.jobDetailId?.let { jobDetailId ->
                jobDetailRepository.findById(jobDetailId).orElse(null)?.jobDetailName
            }

            val missionMemberProfile = MissionMemberProfile(
                age = memberProfileEntity.birthDt?.let { calculateAge(it) },
                gender = memberProfileEntity.gender?.name,
                jobName = jobName,
                jobDetailName = jobDetailName
            )

            // 3. 멤버 관심사 조회 (member_interest 테이블의 id로 조회)
            val memberInterest = memberInterestRepository.findById(request.memberInterestId).orElse(null)
                ?: return@runBlocking ResponseEntity.badRequest().body(
                    ApiResponse<MissionRecommendationResponse>(
                        success = false,
                        data = null,
                        errorMessage = "멤버 관심사를 찾을 수 없습니다. (memberInterestId: ${request.memberInterestId})"
                    )
                ).also {
                    logger.error("멤버 관심사를 찾을 수 없음 - memberInterestId: ${request.memberInterestId}")
                }

            // 3-1. 해당 관심사가 현재 사용자의 것인지 확인
            if (memberInterest.memberId != principal.id) {
                return@runBlocking ResponseEntity.badRequest().body(
                    ApiResponse<MissionRecommendationResponse>(
                        success = false,
                        data = null,
                        errorMessage = "해당 관심사에 접근 권한이 없습니다."
                    )
                ).also {
                    logger.error("관심사 접근 권한 없음 - memberInterestId: ${request.memberInterestId}, 요청자: ${principal.id}, 소유자: ${memberInterest.memberId}")
                }
            }

            val interestPath = memberInterest.directFullPath?.let { path ->
                InterestPath(
                    mainCategory = path.getOrNull(0) ?: "",
                    middleCategory = path.getOrNull(1),
                    subCategory = path.getOrNull(2)
                )
            } ?: return@runBlocking ResponseEntity.badRequest().body(
                ApiResponse<MissionRecommendationResponse>(
                    success = false,
                    data = null,
                    errorMessage = "관심사 경로 정보가 없습니다. (memberInterestId: ${request.memberInterestId})"
                )
            ).also {
                logger.error("관심사 경로 정보가 없음 - memberInterestId: ${request.memberInterestId}")
            }

            logger.info("관심사 경로: ${interestPath.toPathString()}")

            // 4. 제외할 미션 ID 수집
            // 4-1. ACTIVE 상태인 기존 미션 조회
            val activeMissionIds = memberMissionRepository.findMissionIdsByMemberIdAndStatus(
                memberId = principal.id,
                status = MissionStatus.ACTIVE
            )
            logger.info("ACTIVE 상태 미션 ID: $activeMissionIds")

            // 4-2. Redis에서 이전 추천 미션 ID 조회 (interestId 기반)
            val cachedMissionIds = todayMissionCacheService.getRecommendedMissionIds(
                memberId = principal.id,
                interestId = memberInterest.interestId
            )
            logger.info("Redis 캐시 미션 ID: $cachedMissionIds")

            // 4-3. 합치기 (중복 제거)
            val excludeIds = (activeMissionIds + cachedMissionIds).distinct()
            logger.info("제외할 미션 ID 총합: ${excludeIds.size}개")

            // 5. 미션 추천 (난이도 1~5 각각 1개씩)
            val missionDtos = missionRecommendationService.recommendTodayMissions(
                interestPath = interestPath,
                memberProfile = missionMemberProfile,
                difficulty = null,  // 난이도 자동 할당 (1~5)
                excludeIds = excludeIds
            )

            // 6. 추천된 미션 ID를 Redis에 저장
            val recommendedMissionIds = missionDtos.mapNotNull { it.id }
            if (recommendedMissionIds.isNotEmpty()) {
                todayMissionCacheService.saveRecommendedMissionIds(
                    memberId = principal.id,
                    interestId = memberInterest.interestId,
                    missionIds = recommendedMissionIds
                )
            }

            // 7. reset_mission_count 증가
            memberInterest.incrementResetMissionCount()
            memberInterestRepository.save(memberInterest)
            logger.info("reset_mission_count 증가: ${memberInterest.resetMissionCount}")

            // 8. 응답 생성
            val missionGroup = com.haruUp.missionembedding.dto.MissionGroupDto(
                seqNo = 1,
                data = missionDtos
            )

            val response = MissionRecommendationResponse(
                missions = listOf(missionGroup),
                totalCount = missionDtos.size
            )

            logger.info("오늘의 미션 추천 성공: ${missionDtos.size}개")

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
            logger.error("오늘의 미션 추천 실패: ${e.message}", e)
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
     * 생년월일로부터 나이 계산
     */
    private fun calculateAge(birthDt: LocalDateTime): Int {
        val birthDate = birthDt.toLocalDate()
        val now = LocalDateTime.now().toLocalDate()
        return Period.between(birthDate, now).years
    }
}