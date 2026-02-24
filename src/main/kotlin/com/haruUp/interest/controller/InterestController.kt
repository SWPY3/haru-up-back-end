package com.haruUp.interest.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.haruUp.interest.dto.*
import java.security.Principal
import com.haruUp.interest.service.HybridInterestRecommendationService
import com.haruUp.global.ratelimit.RateLimit
import com.haruUp.member.infrastructure.MemberProfileRepository
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import com.haruUp.global.security.MemberPrincipal
import com.haruUp.global.util.TypoValidationCheck
import kotlinx.coroutines.newSingleThreadContext
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.StringRedisTemplate
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Date

/**
 * 관심사 API Controller
 *
 * RAG + AI 하이브리드 추천 시스템 API
 */
@Tag(name = "관심사 API", description = "RAG + AI 하이브리드 관심사 추천 시스템")
@RestController
@RequestMapping("/api/interests")
class InterestController(
    private val recommendationService: HybridInterestRecommendationService,
    private val memberProfileRepository: MemberProfileRepository,
    private val memberInterestRepository: com.haruUp.interest.repository.MemberInterestJpaRepository,
    private val interestEmbeddingRepository: com.haruUp.interest.repository.InterestEmbeddingJpaRepository,
    private val stringRedisTemplate: StringRedisTemplate,
    private val typoValidationCheck: TypoValidationCheck,
    private val memberInterestUseCase: com.haruUp.interest.useCase.MemberInterestUseCase,
    private val jobRepository: com.haruUp.category.repository.JobJpaRepository,
    private val jobDetailRepository: com.haruUp.category.repository.JobDetailRepository
) {
    private val logger = LoggerFactory.getLogger(javaClass)


    /**
     * 관심사 추천 API
     *
     * RAG + AI 하이브리드 방식으로 관심사 추천
     *
     * @param request 추천 요청
     * @return 추천된 관심사 목록
     */
    @Operation(
        summary = "관심사 추천",
        description = """
            AI 기반으로 관심사를 추천합니다.

            **호출 예시:**
            ```json
            {
              "category": [{"directFullPath": ["자격증 공부", "직무 전문 분야"], "job_id": 1, "job_detail_id": 17}],
              "currentLevel": "SUB",
              "targetCount": 10
            }
            ```

            - job_id, job_detail_id는 선택사항이며, 있으면 해당 직업에 맞는 관심사를 추천합니다.
        """
    )
    @RateLimit(key = "api:interests:recommend", limit = 50)
    @PostMapping("/recommend")
    fun recommendInterests(
        @AuthenticationPrincipal principal: MemberPrincipal,
        @Parameter(
            description = "관심사 추천 요청 정보",
            required = true,
            schema = Schema(implementation = InterestRecommendationRequest::class)
        )
        @RequestBody request: InterestRecommendationRequest
    ): ResponseEntity<InterestRecommendationResponse> = runBlocking {
        logger.info("관심사 추천 요청 - 사용자: ${principal.id}, 레벨: ${request.currentLevel}, 목표: ${request.targetCount}개")

        try {
            // DB에서 사용자 프로필 조회
            val memberProfile = memberProfileRepository.findByMemberId(principal.id)
                ?: return@runBlocking ResponseEntity.badRequest().build<InterestRecommendationResponse>().also {
                    logger.error("사용자 프로필을 찾을 수 없음: ${principal.id}")
                }

            logger.info("사용자 프로필 조회 완료 - 생년월일: ${memberProfile.birthDt}, 성별: ${memberProfile.gender}")

            val currentLevel = InterestLevel.valueOf(request.currentLevel)

            val allInterests = mutableListOf<Map<String, Any?>>()

            // 각 category에 대해 개별적으로 추천
            val targetCountPerCategory = request.targetCount / request.category.size.coerceAtLeast(1)

            for (categoryDto in request.category) {
                // directFullPath 기반으로 InterestPath 생성
                val selectedInterest = InterestPath(
                    mainCategory = categoryDto.directFullPath.getOrNull(0) ?: "",
                    middleCategory = categoryDto.directFullPath.getOrNull(1),
                    subCategory = categoryDto.directFullPath.getOrNull(2)
                )

                // job 정보 조회 (있는 경우에만)
                val jobName = categoryDto.jobId?.let { jobId ->
                    jobRepository.findById(jobId).orElse(null)?.jobName
                }
                val jobDetailName = categoryDto.jobDetailId?.let { jobDetailId ->
                    jobDetailRepository.findById(jobDetailId).orElse(null)?.jobDetailName
                }

                logger.info("처리 중: directFullPath=${categoryDto.directFullPath}, job=$jobName, jobDetail=$jobDetailName")

                val interests = recommendationService.recommend(
                    selectedInterests = listOf(selectedInterest),
                    currentLevel = currentLevel,
                    targetCount = targetCountPerCategory,
                    memberProfile = memberProfile,
                    jobName = jobName,
                    jobDetailName = jobDetailName
                )

                // 결과를 Map으로 변환
                val interestsAsMap = interests.map { node ->
                    mapOf(
                        "id" to node.id,
                        "name" to node.name,
                        "level" to node.level.name,
                        "parentId" to node.parentId,
                        "fullPath" to node.fullPath
                    )
                }
                allInterests.addAll(interestsAsMap)

                logger.info("추천 완료: ${interests.size}개")
            }

            val response = InterestRecommendationResponse(
                interests = allInterests,
                totalCount = allInterests.size
            )

            logger.info("전체 추천 성공: ${allInterests.size}개")

            ResponseEntity.ok(response)

        } catch (e: IllegalArgumentException) {
            logger.error("잘못된 요청: ${e.message}")
            ResponseEntity.badRequest().build()
        } catch (e: Exception) {
            logger.error("추천 실패: ${e.message}", e)
            ResponseEntity.internalServerError().build()
        }
    }

    /**
     * 멤버 관심사 조회 API
     *
     * 사용자가 선택한 관심사 목록을 조회합니다 (vector 데이터 제외)
     *
     * @param memberInterestId 멤버 관심사 ID (선택) - 값이 있으면 해당 ID의 관심사만 조회
     * @return 사용자가 선택한 관심사 목록
     */
    @Operation(
        summary = "멤버 관심사 조회",
        description = """
            사용자가 선택한 관심사 목록을 조회합니다.

            - memberInterestId 없음: 모든 관심사 조회
            - memberInterestId 있음: 해당 ID의 관심사만 조회
        """
    )
    @GetMapping("/member")
    fun getMemberInterests(
        @AuthenticationPrincipal principal: MemberPrincipal,
        @Parameter(
            description = "멤버 관심사 ID (없으면 전체 조회)",
            required = false
        )
        @RequestParam(required = false) memberInterestId: Long?
    ): ResponseEntity<MemberInterestsResponse> {
        logger.info("멤버 관심사 조회 - memberId: ${principal.id}, memberInterestId: $memberInterestId")

        return try {
            // memberInterestId가 있으면 특정 관심사만 조회, 없으면 전체 조회
            val memberInterests = if (memberInterestId != null) {
                val singleInterest = memberInterestRepository.findByIdAndMemberIdAndDeletedFalse(
                    id = memberInterestId,
                    memberId = principal.id
                )
                if (singleInterest != null) listOf(singleInterest) else emptyList()
            } else {
                memberInterestRepository.findByMemberIdAndDeletedFalse(principal.id)
            }

            if (memberInterests.isEmpty()) {
                logger.info("멤버 관심사 조회 완료 - memberId: ${principal.id}, 관심사 없음")
                return ResponseEntity.ok(MemberInterestsResponse(emptyList(), 0))
            }

            // 각 관심사에 대해 interest_embeddings에서 full_path 조회
            val interests = memberInterests.map { memberInterest ->
                val interestEmbedding = interestEmbeddingRepository.findById(memberInterest.interestId).orElse(null)
                MemberInterestDto(
                    member_interest_id = memberInterest.id!!,
                    memberId = memberInterest.memberId,
                    interestId = memberInterest.interestId,
                    directFullPath = memberInterest.directFullPath,
                    interestType = memberInterest.interestType.name,
                    resetMissionCount = memberInterest.resetMissionCount,
                    createdAt = memberInterest.createdAt,
                    updatedAt = memberInterest.updatedAt,
                    fullPath = interestEmbedding?.fullPath
                )
            }

            logger.info("멤버 관심사 조회 완료 - memberId: ${principal.id}, count: ${interests.size}")

            ResponseEntity.ok(MemberInterestsResponse(interests, interests.size))

        } catch (e: Exception) {
            logger.error("멤버 관심사 조회 실패: ${e.message}", e)
            ResponseEntity.internalServerError().build()
        }
    }

    /**
     * 멤버 관심사 수정 API
     *
     * memberInterestId에 해당하는 관심사의 interestId와 directFullPath를 수정합니다.
     */
    @Operation(
        summary = "멤버 관심사 수정",
        description = """
            멤버 관심사를 수정합니다.

            **호출 예시:**
            ```json
            {
              "interestId": 64,
              "directFullPath": ["체력관리 및 운동", "헬스", "근력 키우기"]
            }
            ```
        """
    )
    @PutMapping("/member/{memberInterestId}")
    fun updateMemberInterest(
        @AuthenticationPrincipal principal: MemberPrincipal,
        @PathVariable memberInterestId: Long,
        @RequestBody request: MemberInterestUpdateRequest
    ): ResponseEntity<com.haruUp.global.common.ApiResponse<String>> {
        logger.info("멤버 관심사 수정 - memberId: ${principal.id}, memberInterestId: $memberInterestId")

        return try {
            // 해당 관심사 조회 (소유권 확인 포함)
            val memberInterest = memberInterestRepository.findByIdAndMemberIdAndDeletedFalse(
                id = memberInterestId,
                memberId = principal.id
            ) ?: return ResponseEntity.badRequest().body(
                com.haruUp.global.common.ApiResponse.failure("관심사를 찾을 수 없습니다. memberInterestId: $memberInterestId")
            )

            // 새로운 interestId 유효성 검증 (SUB 레벨만 허용)
            val newInterest = interestEmbeddingRepository.findById(request.interestId).orElse(null)
                ?: return ResponseEntity.badRequest().body(
                    com.haruUp.global.common.ApiResponse.failure("유효하지 않은 interestId: ${request.interestId}")
                )

            if (newInterest.level != InterestLevel.SUB) {
                return ResponseEntity.badRequest().body(
                    com.haruUp.global.common.ApiResponse.failure("소분류(SUB) 레벨의 관심사만 등록 가능합니다.")
                )
            }

            // 수정
            memberInterest.update(request.interestId, request.directFullPath)
            memberInterestRepository.save(memberInterest)

            logger.info("멤버 관심사 수정 완료 - memberInterestId: $memberInterestId, newInterestId: ${request.interestId}")

            ResponseEntity.ok(com.haruUp.global.common.ApiResponse.success("수정 성공"))

        } catch (e: Exception) {
            logger.error("멤버 관심사 수정 실패: ${e.message}", e)
            ResponseEntity.internalServerError().body(
                com.haruUp.global.common.ApiResponse.failure("수정 실패: ${e.message}")
            )
        }
    }

    /**
     * 멤버 관심사 삭제 API (관심사 + 관련 미션 soft delete)
     */
    @Operation(
        summary = "멤버 관심사 삭제",
        description = "멤버 관심사와 관련 미션을 함께 삭제합니다. (soft delete)"
    )
    @DeleteMapping("/member/{memberInterestId}")
    fun deleteMemberInterest(
        @AuthenticationPrincipal principal: MemberPrincipal,
        @PathVariable memberInterestId: Long
    ): ResponseEntity<com.haruUp.global.common.ApiResponse<String>> {
        logger.info("멤버 관심사 삭제 - memberId: ${principal.id}, memberInterestId: $memberInterestId")

        return try {
            memberInterestUseCase.deleteInterest(principal.id, memberInterestId)

            ResponseEntity.ok(com.haruUp.global.common.ApiResponse.success("삭제 성공"))

        } catch (e: IllegalArgumentException) {
            logger.warn("멤버 관심사 삭제 실패 - memberId: ${principal.id}, reason: ${e.message}")
            ResponseEntity.badRequest().body(
                com.haruUp.global.common.ApiResponse.failure(e.message ?: "잘못된 요청입니다.")
            )
        } catch (e: Exception) {
            logger.error("멤버 관심사 삭제 실패: ${e.message}", e)
            ResponseEntity.internalServerError().body(
                com.haruUp.global.common.ApiResponse.failure("삭제 실패: ${e.message}")
            )
        }
    }

    /**
     * 시스템 관심사 조회 API
     *
     * created_source가 SYSTEM인 관심사 목록을 조회합니다
     *
     * @param parentId 부모 관심사 ID - 선택 (없으면 대분류 조회)
     * @return 시스템 관심사 목록
     */
    @Operation(
        summary = "관심사 조회",
        description = """
            관심사 목록을 조회합니다.

            - parentId 없음: 대분류(MAIN) 조회 (parent_id가 NULL인 것)
            - parentId 있음: 해당 부모 ID의 자식 관심사 조회

            예시:
            - 대분류 조회: /api/interests/data
            - ID 5('직무 관련 역량 개발')의 중분류 조회: /api/interests/data?parentId=5
        """
    )
    @GetMapping("/data")
    fun getInterestsData(
        @Parameter(
            description = "부모 관심사 ID (없으면 대분류 조회)",
            required = false,
            example = "5"
        )
        @RequestParam(required = false) parentId: Long?
    ): ResponseEntity<InterestsDataResponse> {
        logger.info("관심사 데이터 조회 - parentId: $parentId")

        return try {
            val entities = if (parentId == null) {
                // parentId가 없으면 대분류(parent_id IS NULL) 조회
                interestEmbeddingRepository.findByCreatedSourceAndParentIdIsNullAndIsActivated(
                    createdSource = "SYSTEM",
                    isActivated = true
                )
            } else {
                // parentId가 있으면 해당 부모 ID의 자식 조회
                interestEmbeddingRepository.findByCreatedSourceAndParentIdAndIsActivated(
                    createdSource = "SYSTEM",
                    parentId = parentId.toString(),
                    isActivated = true
                )
            }

            val interests = entities.map { interest ->
                InterestDataDto(
                    id = interest.id!!,
                    parentId = interest.parentId?.toLongOrNull(),
                    level = interest.level.name,
                    name = interest.name,
                    usageCount = interest.usageCount
                )
            }

            logger.info("관심사 데이터 조회 완료 - count: ${interests.size}")

            ResponseEntity.ok(InterestsDataResponse(interests, interests.size))

        } catch (e: Exception) {
            logger.error("관심사 데이터 조회 실패: ${e.message}", e)
            ResponseEntity.internalServerError().build()
        }
    }

    /**
     * 멤버 관심사 저장 API
     *
     * @param principal 인증된 사용자 정보
     * @param request 관심사 저장 요청
     * @return 저장 결과
     */
    @Operation(
        summary = "멤버 관심사 등록",
        description = """
            사용자가 선택한 관심사를 등록합니다. 소분류(SUB) 레벨의 관심사만 등록 가능합니다.

            - interestType 미입력 또는 "PRIMARY": 메인 관심사로 등록
            - interestType = "SUB": 서브 관심사로 등록 (메인 관심사가 먼저 등록되어 있어야 하며, 최대 1개)
            - 미션 생성은 별도로 POST /api/missions/recommend 를 호출해주세요

            **메인 관심사 등록 예시:**
            ```json
            {
              "interests": [{"interestId": 64, "directFullPath": ["체력관리 및 운동", "헬스", "근력 키우기"]}]
            }
            ```

            **서브 관심사 등록 예시:**
            ```json
            {
              "interests": [{"interestId": 125, "directFullPath": ["직무 관련 역량 개발", "리더십", "성과관리 능력 향상"]}],
              "interestType": "SUB"
            }
            ```
        """
    )
    @PostMapping("/member")
    fun saveMemberInterests(
        @AuthenticationPrincipal principal: MemberPrincipal,
        @RequestBody request: MemberInterestSaveRequest
    ): ResponseEntity<com.haruUp.global.common.ApiResponse<String>> {
        val memberId = principal.id

        return try {
            val interestType = try {
                com.haruUp.interest.entity.InterestType.valueOf(request.interestType)
            } catch (e: IllegalArgumentException) {
                com.haruUp.interest.entity.InterestType.PRIMARY
            }

            val interests = request.interests.map { dto ->
                Pair(
                    dto.interestId ?: throw IllegalArgumentException("interestId는 필수입니다."),
                    dto.directFullPath.takeIf { it.isNotEmpty() }
                )
            }

            val result = memberInterestUseCase.saveInterests(memberId, interests, interestType)

            if (result.hasInvalidInterests) {
                ResponseEntity.badRequest().body(
                    com.haruUp.global.common.ApiResponse.failure(
                        "소분류(SUB) 레벨의 관심사만 저장 가능합니다. 잘못된 interestId: ${result.invalidInterestIds}"
                    )
                )
            } else {
                ResponseEntity.ok(
                    com.haruUp.global.common.ApiResponse.success("${result.savedCount}건 저장 성공")
                )
            }
        } catch (e: IllegalArgumentException) {
            logger.warn("멤버 관심사 저장 실패 - memberId: $memberId, reason: ${e.message}")
            ResponseEntity.badRequest().body(
                com.haruUp.global.common.ApiResponse.failure(e.message ?: "잘못된 요청입니다.")
            )
        } catch (e: Exception) {
            logger.error("멤버 관심사 저장 실패: ${e.message}", e)
            ResponseEntity.internalServerError().body(
                com.haruUp.global.common.ApiResponse.failure("저장 실패: ${e.message}")
            )
        }
    }


    /* =========================
     * 관심사 문자열 검증 API
     * ========================= */
    @Operation(
        summary = "관심사 문자열 유효성 검증",
        description = "한글 기반 관심사 문자열의 유효성과 의미를 검증합니다."
    )
    @PostMapping("/interest/validation")
    fun interestValidationCheck(
        @AuthenticationPrincipal principal: MemberPrincipal,
        @RequestBody request: InterestValidationRequest
    ): ResponseEntity<com.haruUp.global.common.ApiResponse<InterestValidationResponse>> {

        val memberId = principal.id

        val checkRedisCount = typoValidationCheck.checkRedisCount(memberId);
        if(checkRedisCount != null) {
            val response = InterestValidationResponse(
                isValid = false,
                reason = "오타 발견"
            )
            return ResponseEntity.ok(
                com.haruUp.global.common.ApiResponse.success(response)
            )
        }

        val result = typoValidationCheck.validateKoreanText(request.interest)
        val response = InterestValidationResponse(
            isValid = result.isValid,
            reason = result.reason
        )

        return ResponseEntity.ok(
            com.haruUp.global.common.ApiResponse.success(response)
        )
    }


    /* =========================
     * Request / Response DTO
     * ========================= */

    data class InterestValidationRequest(
        @Schema(
            description = "검증할 관심사 문자열",
            example = "근력 키우기"
        )
        val interest: String
    )

    data class InterestValidationResponse(
        val isValid: Boolean,
        val reason: String?
    )




}
