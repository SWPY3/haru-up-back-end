package com.haruUp.interest.controller

import com.haruUp.interest.dto.*

import com.haruUp.interest.model.InterestLevel
import com.haruUp.interest.service.HybridInterestRecommendationService
import com.haruUp.global.ratelimit.RateLimit
import com.haruUp.member.infrastructure.MemberProfileRepository
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import com.haruUp.global.security.MemberPrincipal

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
    private val interestEmbeddingRepository: com.haruUp.interest.repository.InterestEmbeddingJpaRepository
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
              "category": [{"seqNo": 1, "mainCategory": "운동", "middleCategory": "헬스"}],
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

            // category가 비어있거나 seqNo가 없으면 기존 방식
            if (request.category.isEmpty() || request.category.all { it.seqNo == null }) {
                // 기존 로직: 모든 category를 한 번에 처리
                val selectedInterests = request.category.map { it.toModel() }
                logger.info("선택된 관심사 경로: ${selectedInterests.map { it.toPathString() }}")

                val result = recommendationService.recommend(
                    selectedInterests = selectedInterests,
                    currentLevel = currentLevel,
                    targetCount = request.targetCount,
                    memberProfile = memberProfile,
                    useHybridScoring = useHybridScoring
                )

                val response = InterestRecommendationResponse(
                    interests = result.interests.map { InterestNodeDto.from(it) },
                    ragCount = result.ragCount,
                    aiCount = result.aiCount,
                    totalCount = result.totalCount,
                    ragRatio = result.ragRatio,
                    aiRatio = result.aiRatio,
                    usedHybridScoring = result.usedHybridScoring
                )

                logger.info("추천 성공: ${response.summary}")
                return@runBlocking ResponseEntity.ok(response)
            }

            // 새로운 로직: category별로 추천 처리 (seqNo 추적)
            logger.info("category별 추천 처리 시작 - ${request.category.size}개")

            val allInterests = mutableListOf<InterestNodeDto>()
            var totalRagCount = 0
            var totalAiCount = 0

            // 각 category에 대해 개별적으로 추천
            val targetCountPerCategory = request.targetCount / request.category.size.coerceAtLeast(1)

            for (categoryDto in request.category) {
                val selectedInterest = categoryDto.toModel()
                logger.info("처리 중: seqNo=${categoryDto.seqNo}, path=${selectedInterest.toPathString()}")

                val result = recommendationService.recommend(
                    selectedInterests = listOf(selectedInterest),
                    currentLevel = currentLevel,
                    targetCount = targetCountPerCategory,
                    memberProfile = memberProfile,
                    useHybridScoring = useHybridScoring
                )

                // seqNo를 포함해서 DTO 변환
                val interestsWithSeqNo = result.interests.map { InterestNodeDto.from(it, categoryDto.seqNo) }
                allInterests.addAll(interestsWithSeqNo)

                totalRagCount += result.ragCount
                totalAiCount += result.aiCount

                logger.info("seqNo=${categoryDto.seqNo} 추천 완료: RAG=${result.ragCount}, AI=${result.aiCount}")
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
            val memberInterests = memberInterestRepository.findByMemberId(principal.id)

            if (memberInterests.isEmpty()) {
                logger.info("멤버 관심사 조회 완료 - memberId: ${principal.id}, 관심사 없음")
                return ResponseEntity.ok(MemberInterestsResponse(emptyList(), 0))
            }

            // 각 관심사에 대해 interest_embeddings에서 상세 정보 조회
            val interests = memberInterests.mapNotNull { memberInterest ->
                val interestEmbedding = interestEmbeddingRepository.findById(memberInterest.interestId).orElse(null)
                interestEmbedding?.let {
                    MemberInterestDto(
                        id = it.id.toString(),
                        name = it.name,
                        level = it.level.name,
                        parentId = it.parentId,
                        fullPath = it.fullPath,
                        usageCount = it.usageCount,
                        isActivated = it.isActivated
                    )
                }
            }

            logger.info("멤버 관심사 조회 완료 - memberId: ${principal.id}, count: ${interests.size}")

            ResponseEntity.ok(MemberInterestsResponse(interests, interests.size))

        } catch (e: Exception) {
            logger.error("멤버 관심사 조회 실패: ${e.message}", e)
            ResponseEntity.internalServerError().build()
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
                    parentId = parentId,
                    isActivated = true
                )
            }

            val interests = entities.map { interest ->
                InterestDataDto(
                    id = interest.id!!,
                    parentId = interest.parentId,
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
        summary = "멤버 관심사 저장",
        description = "사용자가 선택한 관심사를 저장합니다."
    )
    @PostMapping("/member")
    fun saveMemberInterests(
        @AuthenticationPrincipal principal: MemberPrincipal,
        @RequestBody request: MemberInterestSaveRequest
    ): ResponseEntity<com.haruUp.global.common.ApiResponse<String>> {
        val memberId = principal.id

        try {
            var savedCount = 0

            for (interestPath in request.interests) {
                // 대분류, 중분류, 소분류 모두 입력되었는지 확인
                if (interestPath.directFullPath.size != 3) {
                    logger.warn("대분류, 중분류, 소분류가 모두 입력되어야 합니다: ${interestPath.directFullPath}")
                    continue
                }

                val fullPathStr = "{${interestPath.directFullPath.joinToString(",")}}"
                val interestId = interestEmbeddingRepository.findIdByFullPath(fullPathStr)

                if (interestId != null) {
                    // 이미 등록된 관심사인지 확인
                    val exists = memberInterestRepository.existsByMemberIdAndInterestId(memberId, interestId)
                    if (!exists) {
                        val memberInterest = com.haruUp.interest.entity.MemberInterestEntity(
                            memberId = memberId,
                            interestId = interestId,
                            directFullPath = interestPath.directFullPath
                        )
                        memberInterestRepository.save(memberInterest)
                        savedCount++
                    }
                } else {
                    logger.warn("관심사를 찾을 수 없습니다: ${interestPath.directFullPath}")
                }
            }

            logger.info("멤버 관심사 저장 완료 - memberId: $memberId, savedCount: $savedCount")
            return ResponseEntity.ok(
                com.haruUp.global.common.ApiResponse.success("${savedCount}건 저장 성공")
            )
        } catch (e: Exception) {
            logger.error("멤버 관심사 저장 실패: ${e.message}", e)
            return ResponseEntity.internalServerError().body(
                com.haruUp.global.common.ApiResponse.failure("저장 실패: ${e.message}")
            )
        }
    }
}
