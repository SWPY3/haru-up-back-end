package com.haruUp.missionembedding.controller

import com.haruUp.global.common.ApiResponse as CommonApiResponse
import com.haruUp.global.security.MemberPrincipal
import org.springframework.security.core.annotation.AuthenticationPrincipal
import com.haruUp.missionembedding.dto.MissionRecommendationRequest
import com.haruUp.missionembedding.dto.MissionRecommendationResponse
import com.haruUp.missionembedding.dto.TodayMissionRecommendationRequest
import com.haruUp.missionembedding.dto.MissionSelectionRequest
import com.haruUp.missionembedding.repository.MissionEmbeddingRepository
import com.haruUp.missionembedding.service.MissionRecommendationService
import com.haruUp.missionembedding.service.MissionSelectionService
import com.haruUp.missionembedding.service.TodayMissionCacheService
import com.haruUp.mission.domain.MissionStatus
import com.haruUp.global.clova.MissionMemberProfile
import com.haruUp.global.ratelimit.RateLimit
import com.haruUp.member.infrastructure.MemberProfileRepository
import com.haruUp.mission.infrastructure.MemberMissionRepository
import com.haruUp.category.repository.JobRepository
import com.haruUp.category.repository.JobDetailRepository
import com.haruUp.interest.repository.MemberInterestJpaRepository
import com.haruUp.interest.model.InterestPath
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.LocalDateTime
import java.time.Period

/**
 * 미션 API Controller
 */
@Tag(name = "미션 API", description = "AI 기반 미션 추천 및 선택 시스템")
@RestController
@RequestMapping("/api/missions")
class MissionembeddingController(
    private val missionRecommendationService: MissionRecommendationService,
    private val missionSelectionService: MissionSelectionService,
    private val todayMissionCacheService: TodayMissionCacheService,
    private val memberProfileRepository: MemberProfileRepository,
    private val memberMissionRepository: MemberMissionRepository,
    private val missionEmbeddingRepository: MissionEmbeddingRepository,
    private val jobRepository: JobRepository,
    private val jobDetailRepository: JobDetailRepository,
    private val memberInterestRepository: MemberInterestJpaRepository
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 미션 추천 API
     *
     * 사용자가 선택한 관심사를 기반으로 각 관심사당 미션 5개씩 추천
     *
     * @param request 미션 추천 요청
     * @return 추천된 미션 목록
     */
    @Operation(
        summary = "미션 추천",
        description = """
            사용자가 선택한 관심사를 기반으로 AI가 미션을 추천합니다.

            각 관심사당 난이도 1~5 각각 1개씩, 총 5개의 미션이 추천됩니다.

            **호출 예시:**
            ```json
            {
              "interests": [
                {"seqNo": 1, "directFullPath": ["체력관리 및 운동", "헬스", "근력 키우기"]},
                {"seqNo": 2, "directFullPath": ["외국어 공부", "영어", "단어 학습"]}
              ]
            }
            ```

            **응답 예시 (각 관심사당 난이도 1~5):**
            - difficulty 1: 중학생 수준 (하루 5개, 10분)
            - difficulty 2: 고등학생 수준 (하루 10개, 20분)
            - difficulty 3: 대학생 수준 (하루 20개, 30분)
            - difficulty 4: 직장인 수준 (하루 50개, 1시간)
            - difficulty 5: 전문가 수준 (하루 100개, 2시간)
        """
    )
    @RateLimit(key = "api:missions:recommend", limit = 50)
    @PostMapping("/recommend")
    fun recommendMissions(
        @AuthenticationPrincipal principal: MemberPrincipal,
        @Parameter(
            description = "미션 추천 요청 정보",
            required = true,
            schema = Schema(implementation = MissionRecommendationRequest::class)
        )
        @RequestBody request: MissionRecommendationRequest
    ): ResponseEntity<MissionRecommendationResponse> = runBlocking {
        logger.info("미션 추천 요청 - 사용자: ${principal.id}, 관심사 개수: ${request.interests.size}")

        try {
            // DB에서 사용자 프로필 조회
            val memberProfileEntity = memberProfileRepository.findByMemberId(principal.id)
                ?: return@runBlocking ResponseEntity.badRequest().build<MissionRecommendationResponse>().also {
                    logger.error("사용자 프로필을 찾을 수 없음: ${principal.id}")
                }

            // 직업 정보 조회
            val jobName = memberProfileEntity.jobId?.let { jobId ->
                jobRepository.findById(jobId).orElse(null)?.jobName
            }
            val jobDetailName = memberProfileEntity.jobDetailId?.let { jobDetailId ->
                jobDetailRepository.findById(jobDetailId).orElse(null)?.jobDetailName
            }

            // MemberProfileEntity → MissionMemberProfile 변환 (직업, 성별 정보 포함)
            val missionMemberProfile = MissionMemberProfile(
                age = memberProfileEntity.birthDt?.let { calculateAge(it) },
                gender = memberProfileEntity.gender?.name,
                jobName = jobName,
                jobDetailName = jobDetailName
            )

            logger.info("사용자 프로필 조회 완료 - 나이: ${missionMemberProfile.age}, 성별: ${missionMemberProfile.gender}, 직업: ${missionMemberProfile.jobName}")

            // interests를 (seqNo, InterestPath) 튜플로 변환 (난이도는 자동으로 1~5 할당)
            val interestsWithDetails = request.interests.map { dto ->
                Pair(dto.seqNo, dto.toModel())
            }

            // 미션 추천 (각 관심사당 난이도 1~5 각각 1개씩)
            val missions = missionRecommendationService.recommendMissions(
                interests = interestsWithDetails,
                memberProfile = missionMemberProfile
            )

            val response = MissionRecommendationResponse(
                missions = missions,
                totalCount = missions.size
            )

            logger.info("미션 추천 성공: ${missions.size}개")

            ResponseEntity.ok(response)

        } catch (e: IllegalArgumentException) {
            logger.error("잘못된 요청: ${e.message}")
            ResponseEntity.badRequest().build()
        } catch (e: Exception) {
            logger.error("미션 추천 실패: ${e.message}", e)
            ResponseEntity.internalServerError().build()
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
    @RateLimit(key = "api:missions:today-recommend", limit = 50)
    @PostMapping("/today-recommend")
    fun todayRecommendMissions(
        @AuthenticationPrincipal principal: MemberPrincipal,
        @Parameter(
            description = "오늘의 미션 추천 요청 정보",
            required = true,
            schema = Schema(implementation = TodayMissionRecommendationRequest::class)
        )
        @RequestBody request: TodayMissionRecommendationRequest
    ): ResponseEntity<CommonApiResponse<MissionRecommendationResponse>> = runBlocking {
        logger.info("오늘의 미션 추천 요청 - 사용자: ${principal.id}, memberInterestId: ${request.memberInterestId}")

        try {
            // 1. DB에서 사용자 프로필 조회
            val memberProfileEntity = memberProfileRepository.findByMemberId(principal.id)
                ?: return@runBlocking ResponseEntity.badRequest().body(
                    CommonApiResponse<MissionRecommendationResponse>(
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
                    CommonApiResponse<MissionRecommendationResponse>(
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
                    CommonApiResponse<MissionRecommendationResponse>(
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
                CommonApiResponse<MissionRecommendationResponse>(
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

            ResponseEntity.ok(CommonApiResponse.success(response))

        } catch (e: IllegalArgumentException) {
            logger.error("잘못된 요청: ${e.message}")
            ResponseEntity.badRequest().body(
                CommonApiResponse(
                    success = false,
                    data = null,
                    errorMessage = e.message ?: "잘못된 요청입니다."
                )
            )
        } catch (e: Exception) {
            logger.error("오늘의 미션 추천 실패: ${e.message}", e)
            ResponseEntity.internalServerError().body(
                CommonApiResponse(
                    success = false,
                    data = null,
                    errorMessage = "서버 오류가 발생했습니다."
                )
            )
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
                  "interestId": 97,
                  "directFullPath": [
                    "직무 관련 역량 개발",
                    "업무 능력 향상",
                    "문서·기획·정리 스킬 향상(PPT·보고서)"
                  ],
                  "difficulty": 1,
                  "mission": "보고서 작성법 관련 책 1권 읽고 요약 정리하기"
                }
              ]
            }
            ```

            **필드 설명:**
            - interestId: 소분류 관심사 ID (반드시 소분류 관심사 interestId로 입력해주세요.)
            - directFullPath: 관심사 경로 배열 [대분류, 중분류, 소분류]
            - difficulty: 난이도 (1~5, 선택)
            - mission: 미션 내용
        """
    )
    @PostMapping("/select")
    fun selectMissions(
        @AuthenticationPrincipal principal: MemberPrincipal,
        @Parameter(
            description = "미션 선택 요청 정보",
            required = true,
            schema = Schema(implementation = MissionSelectionRequest::class)
        )
        @RequestBody request: MissionSelectionRequest
    ): ResponseEntity<CommonApiResponse<List<Long>>> {
        logger.info("미션 선택 요청 - 사용자: ${principal.id}, 미션 개수: ${request.missions.size}")

        return try {
            val savedMissionIds = missionSelectionService.saveMissions(principal.id, request)
            logger.info("미션 선택 완료 - 저장된 개수: ${savedMissionIds.size}")
            ResponseEntity.ok(CommonApiResponse.success(savedMissionIds))
        } catch (e: IllegalArgumentException) {
            logger.error("잘못된 요청: ${e.message}")
            ResponseEntity.badRequest().body(
                CommonApiResponse(
                    success = false,
                    data = emptyList(),
                    errorMessage = e.message ?: "유효성 검증 실패"
                )
            )
        } catch (e: Exception) {
            logger.error("미션 선택 실패: ${e.message}", e)
            ResponseEntity.internalServerError().body(
                CommonApiResponse(
                    success = false,
                    data = emptyList(),
                    errorMessage = "서버 오류가 발생했습니다"
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
