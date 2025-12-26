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
    private val typoValidationCheck: TypoValidationCheck
    private val memberInterestUseCase: com.haruUp.interest.useCase.MemberInterestUseCase
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
            RAG + AI 하이브리드 방식으로 관심사를 추천합니다.

            **호출 예시:**
            ```json
            {
              "category": [{"interestId": 1, "directFullPath": ["체력관리 및 운동", "헬스"]}],
              "currentLevel": "SUB",
              "targetCount": 10
            }
            ```
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
        @RequestBody request: InterestRecommendationRequest,
        @Parameter(
            description = "하이브리드 스코어링 사용 여부 (false: 유사도만, true: 유사도+인기도)",
            required = false
        )
        @RequestParam(defaultValue = "false") useHybridScoring: Boolean
    ): ResponseEntity<InterestRecommendationResponse> = runBlocking {
        val scoringMode = if (useHybridScoring) "하이브리드(유사도+인기도)" else "유사도만"
        logger.info("관심사 추천 요청 - 사용자: ${principal.id}, 레벨: ${request.currentLevel}, 목표: ${request.targetCount}개, 스코어링: $scoringMode")

        try {
            // DB에서 사용자 프로필 조회
            val memberProfile = memberProfileRepository.findByMemberId(principal.id)
                ?: return@runBlocking ResponseEntity.badRequest().build<InterestRecommendationResponse>().also {
                    logger.error("사용자 프로필을 찾을 수 없음: ${principal.id}")
                }

            logger.info("사용자 프로필 조회 완료 - 생년월일: ${memberProfile.birthDt}, 성별: ${memberProfile.gender}")

            val currentLevel = InterestLevel.valueOf(request.currentLevel)

            val allInterests = mutableListOf<Map<String, Any?>>()
            var totalRagCount = 0
            var totalAiCount = 0

            // 각 category에 대해 개별적으로 추천
            val targetCountPerCategory = request.targetCount / request.category.size.coerceAtLeast(1)

            for (categoryDto in request.category) {
                // interestId로 entity 조회하여 fullPath 획득
                val interestEntity = interestEmbeddingRepository.findEntityById(categoryDto.interestId)
                if (interestEntity == null) {
                    logger.warn("관심사를 찾을 수 없음: interestId=${categoryDto.interestId}")
                    continue
                }

                val selectedInterest = InterestPath(
                    mainCategory = interestEntity.fullPath.getOrNull(0) ?: "",
                    middleCategory = interestEntity.fullPath.getOrNull(1),
                    subCategory = interestEntity.fullPath.getOrNull(2)
                )
                logger.info("처리 중: interestId=${categoryDto.interestId}, path=${selectedInterest.toPathString()}")

                val result = recommendationService.recommend(
                    selectedInterests = listOf(selectedInterest),
                    currentLevel = currentLevel,
                    targetCount = targetCountPerCategory,
                    memberProfile = memberProfile,
                    useHybridScoring = useHybridScoring
                )

                // 결과를 Map으로 변환
                val interestsAsMap = result.interests.map { node ->
                    mapOf(
                        "id" to node.id,
                        "name" to node.name,
                        "level" to node.level.name,
                        "parentId" to node.parentId,
                        "isEmbedded" to node.isEmbedded,
                        "usageCount" to node.usageCount,
                        "fullPath" to node.fullPath,
                        "interestId" to categoryDto.interestId
                    )
                }
                allInterests.addAll(interestsAsMap)

                totalRagCount += result.ragCount
                totalAiCount += result.aiCount

                logger.info("interestId=${categoryDto.interestId} 추천 완료: RAG=${result.ragCount}, AI=${result.aiCount}")
            }

            val response = InterestRecommendationResponse(
                interests = allInterests,
                ragCount = totalRagCount,
                aiCount = totalAiCount,
                totalCount = allInterests.size,
                ragRatio = if (allInterests.isNotEmpty()) totalRagCount.toDouble() / allInterests.size else 0.0,
                aiRatio = if (allInterests.isNotEmpty()) totalAiCount.toDouble() / allInterests.size else 0.0,
                usedHybridScoring = useHybridScoring
            )

            logger.info("전체 추천 성공: ${response.summary}")

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
     * @return 사용자가 선택한 관심사 목록
     */
    @Operation(
        summary = "멤버 관심사 조회",
        description = "사용자가 선택한 관심사 목록을 조회합니다"
    )
    @GetMapping("/member")
    fun getMemberInterests(
        @AuthenticationPrincipal principal: MemberPrincipal
    ): ResponseEntity<MemberInterestsResponse> {
        logger.info("멤버 관심사 조회 - memberId: ${principal.id}")

        return try {
            // member_interest 테이블에서 사용자가 선택한 관심사 조회
            val memberInterests = memberInterestRepository.findByMemberIdAndDeletedFalse(principal.id)

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
     * 멤버 관심사 삭제 API (soft delete)
     */
    @Operation(
        summary = "멤버 관심사 삭제",
        description = "멤버 관심사를 삭제합니다. (soft delete)"
    )
    @DeleteMapping("/member/{memberInterestId}")
    @org.springframework.transaction.annotation.Transactional
    fun deleteMemberInterest(
        @AuthenticationPrincipal principal: MemberPrincipal,
        @PathVariable memberInterestId: Long
    ): ResponseEntity<com.haruUp.global.common.ApiResponse<String>> {
        logger.info("멤버 관심사 삭제 - memberId: ${principal.id}, memberInterestId: $memberInterestId")

        return try {
            val deletedCount = memberInterestRepository.softDeleteByIdAndMemberId(
                id = memberInterestId,
                memberId = principal.id
            )

            if (deletedCount == 0) {
                return ResponseEntity.badRequest().body(
                    com.haruUp.global.common.ApiResponse.failure("관심사를 찾을 수 없습니다. memberInterestId: $memberInterestId")
                )
            }

            logger.info("멤버 관심사 삭제 완료 - memberInterestId: $memberInterestId")

            ResponseEntity.ok(com.haruUp.global.common.ApiResponse.success("삭제 성공"))

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
            **호출 예시:**
            ```json
            {
              "interests": [
                {"interestId": 10},
                {"interestId": 15}
              ]
            }
            ```
            
            interestId는 관심사 조회 API(/api/interests/data) 또는 관심사 추천 API(/api/interests/recommend)를 통해 획득할 수 있습니다.
        """
    )
    @PostMapping("/member")
    fun saveMemberInterests(
        @AuthenticationPrincipal principal: MemberPrincipal,
        @RequestBody request: MemberInterestSaveRequest
    ): ResponseEntity<com.haruUp.global.common.ApiResponse<String>> {
        val memberId = principal.id

        return try {
            val interestIds = request.interests.map { it.interestId }
            val result = memberInterestUseCase.saveInterests(memberId, interestIds)

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
