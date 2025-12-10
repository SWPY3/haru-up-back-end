package com.haruUp.domain.mission.controller

import com.haruUp.interest.dto.MemberMissionDto
import com.haruUp.interest.dto.MemberMissionsResponse
import com.haruUp.domain.mission.dto.MissionRecommendationRequest
import com.haruUp.domain.mission.dto.MissionRecommendationResponse
import com.haruUp.domain.mission.dto.MissionSelectionRequest
import com.haruUp.domain.mission.dto.MissionSelectionResponse
import com.haruUp.domain.mission.service.MissionRecommendationService
import com.haruUp.domain.mission.service.MissionSelectionService
import com.haruUp.global.clova.MissionUserProfile
import com.haruUp.global.ratelimit.RateLimit
import com.haruUp.member.infrastructure.MemberProfileRepository
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
class MissionController(
    private val missionRecommendationService: MissionRecommendationService,
    private val missionSelectionService: MissionSelectionService,
    private val memberProfileRepository: MemberProfileRepository,
    private val memberMissionRepository: com.haruUp.domain.mission.repository.MemberMissionRepository,
    private val missionEmbeddingRepository: com.haruUp.domain.mission.repository.MissionEmbeddingRepository
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

            **호출 예시:**
            ```json
            {
              "userId": 1,
              "interests": [
                {"seqNo": 1, "mainCategory": "운동", "middleCategory": "헬스", "subCategory": "근력 키우기", "difficulty": 1},
                {"seqNo": 2, "mainCategory": "공부", "middleCategory": "영어", "subCategory": "영어 단어 외우기", "difficulty": 2}
              ]
            }
            ```

            **난이도 기준:**
            - difficulty 없음: 정량적 수치 없는 일반 미션
            - 1: 중학생 수준 (하루 5개, 10분, 주 1회)
            - 2: 고등학생 수준 (하루 10개, 20분, 주 2회)
            - 3: 대학생 수준 (하루 20개, 30분, 주 3회)
            - 4: 직장인/아마추어 수준 (하루 50개, 1시간, 주 5회)
            - 5: 전문가 수준 (하루 100개, 2시간, 매일)
        """
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "추천 성공",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = MissionRecommendationResponse::class)
                )]
            ),
            ApiResponse(
                responseCode = "400",
                description = "잘못된 요청 (유효하지 않은 userId 등)"
            ),
            ApiResponse(
                responseCode = "500",
                description = "서버 에러"
            )
        ]
    )
    @RateLimit(key = "api:missions:recommend", limit = 50)
    @PostMapping("/recommend")
    fun recommendMissions(
        @Parameter(
            description = "미션 추천 요청 정보",
            required = true,
            schema = Schema(implementation = MissionRecommendationRequest::class)
        )
        @RequestBody request: MissionRecommendationRequest
    ): ResponseEntity<MissionRecommendationResponse> = runBlocking {
        logger.info("미션 추천 요청 - 사용자: ${request.userId}, 관심사 개수: ${request.interests.size}")

        try {
            // DB에서 사용자 프로필 조회
            val memberProfile = memberProfileRepository.findByMemberId(request.userId)
                ?: return@runBlocking ResponseEntity.badRequest().build<MissionRecommendationResponse>().also {
                    logger.error("사용자 프로필을 찾을 수 없음: ${request.userId}")
                }

            // MemberProfile → MissionUserProfile 변환
            val missionUserProfile = MissionUserProfile(
                age = memberProfile.birthDt?.let { calculateAge(it) },
                introduction = memberProfile.intro  // intro를 introduction으로 사용
            )

            logger.info("사용자 프로필 조회 완료 - 나이: ${missionUserProfile.age}")

            // interests를 (seqNo, InterestPath, difficulty) 튜플로 변환
            val interestsWithDetails = request.interests.map { dto ->
                Triple(dto.seqNo, dto.toModel(), dto.difficulty)
            }

            // 미션 추천
            val missions = missionRecommendationService.recommendMissions(
                interests = interestsWithDetails,
                userProfile = missionUserProfile
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
              "userId": 1,
              "missions": [
                {
                  "mainCategory": {"text": "운동", "createdSource": "SYSTEM"},
                  "middleCategory": {"text": "사격", "createdSource": "USER"},
                  "subCategory": {"text": "AR 연습하기", "createdSource": "AI"},
                  "difficulty": 1,
                  "mission": "하루 푸쉬업 10개씩 3세트 하기"
                },
                {
                  "mainCategory": {"text": "공부", "createdSource": "SYSTEM"},
                  "middleCategory": {"text": "영어", "createdSource": "SYSTEM"},
                  "subCategory": {"text": "영어 단어 외우기", "createdSource": "USER"},
                  "difficulty": 2,
                  "mission": "하루 20개 영단어와 예문 함께 외우기"
                }
              ]
            }
            ```

            **createdSource 값:**
            - SYSTEM: 시스템에서 제공한 기본 카테고리
            - USER: 사용자가 직접 입력한 카테고리
            - AI: AI가 생성한 카테고리
        """
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "저장 성공",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = MissionSelectionResponse::class)
                )]
            ),
            ApiResponse(
                responseCode = "400",
                description = "잘못된 요청"
            ),
            ApiResponse(
                responseCode = "500",
                description = "서버 에러"
            )
        ]
    )
    @PostMapping("/select")
    fun selectMissions(
        @Parameter(
            description = "미션 선택 요청 정보",
            required = true,
            schema = Schema(implementation = MissionSelectionRequest::class)
        )
        @RequestBody request: MissionSelectionRequest
    ): ResponseEntity<MissionSelectionResponse> {
        logger.info("미션 선택 요청 - 사용자: ${request.userId}, 미션 개수: ${request.missions.size}")

        return try {
            val response = missionSelectionService.saveMissions(request)
            logger.info("미션 선택 완료 - 저장된 개수: ${response.savedCount}")
            ResponseEntity.ok(response)
        } catch (e: Exception) {
            logger.error("미션 선택 실패: ${e.message}", e)
            ResponseEntity.internalServerError().build()
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

    /**
     * 미션 상태 업데이트 API
     *
     * member_mission 테이블의 is_completed 컬럼을 업데이트합니다.
     *
     * @param missionId member_mission 테이블의 ID
     * @param request 완료 여부 요청
     */
    @Operation(
        summary = "미션 상태 업데이트",
        description = """
            미션의 완료/포기 상태를 업데이트합니다.

            **사용 예시:**
            - PUT /api/missions/completed/123
            - Body: {"isCompleted": true}  // 완료
            - Body: {"isCompleted": false} // 포기
        """
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "상태 업데이트 성공"
            ),
            ApiResponse(
                responseCode = "404",
                description = "미션을 찾을 수 없음"
            ),
            ApiResponse(
                responseCode = "500",
                description = "서버 에러"
            )
        ]
    )
    @PutMapping("/completed/{missionId}")
    fun updateMissionStatus(
        @Parameter(
            description = "member_mission 테이블의 ID",
            required = true,
            example = "123"
        )
        @PathVariable missionId: Long,
        @RequestBody request: com.haruUp.interest.dto.UpdateMissionStatusRequest
    ): ResponseEntity<Void> {
        logger.info("미션 상태 업데이트 - missionId: $missionId, isCompleted: ${request.isCompleted}")

        return try {
            // member_mission 조회
            val memberMission = memberMissionRepository.findById(missionId).orElse(null)
                ?: return ResponseEntity.notFound().build<Void>().also {
                    logger.error("미션을 찾을 수 없음: $missionId")
                }

            // is_completed 업데이트
            memberMission.isCompleted = if (request.isCompleted) true else false
            memberMissionRepository.save(memberMission)

            logger.info("미션 상태 업데이트 완료 - missionId: $missionId, isCompleted: ${request.isCompleted}")

            ResponseEntity.ok().build()

        } catch (e: Exception) {
            logger.error("미션 상태 업데이트 실패: ${e.message}", e)
            ResponseEntity.internalServerError().build()
        }
    }

    /**
     * 멤버 미션 조회 API
     *
     * 사용자가 선택한 미션 목록을 조회합니다 (vector 데이터 제외)
     *
     * @param userId 사용자 ID
     * @return 사용자가 선택한 미션 목록
     */
    @Operation(
        summary = "멤버 미션 조회",
        description = "사용자가 선택한 미션 목록을 조회합니다 (임베딩 벡터 데이터는 제외)"
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "조회 성공"
            ),
            ApiResponse(
                responseCode = "404",
                description = "사용자를 찾을 수 없음"
            ),
            ApiResponse(
                responseCode = "500",
                description = "서버 에러"
            )
        ]
    )
    @GetMapping("/member/{userId}")
    fun getMemberMissions(
        @Parameter(
            description = "사용자 ID",
            required = true,
            example = "1"
        )
        @PathVariable userId: Long
    ): ResponseEntity<MemberMissionsResponse> {
        logger.info("멤버 미션 조회 - userId: $userId")

        return try {
            // member_mission 테이블에서 사용자 미션 조회
            val memberMissions = memberMissionRepository.findByMemberId(userId)

            if (memberMissions.isEmpty()) {
                logger.info("멤버 미션 조회 완료 - userId: $userId, 미션 없음")
                return ResponseEntity.ok(MemberMissionsResponse(emptyList(), 0))
            }

            // 각 member_mission에 대해 mission_embeddings 정보 조회 및 결합
            val missions = memberMissions.mapNotNull { memberMission ->
                val missionEmbedding = missionEmbeddingRepository.findById(memberMission.missionId).orElse(null)
                missionEmbedding?.let {
                    MemberMissionDto(
                        memberMissionId = memberMission.id ?: 0L,
                        missionId = it.id ?: 0L,
                        categoryPath = it.getInterestPath(),
                        difficulty = it.difficulty,
                        missionContent = it.missionContent,
                        usageCount = it.usageCount,
                        isCompleted = memberMission.isCompleted ?: false,
                        isActivated = it.isActivated,
                        createdAt = memberMission.createdAt.toString()
                    )
                }
            }

            logger.info("멤버 미션 조회 완료 - userId: $userId, count: ${missions.size}")

            ResponseEntity.ok(MemberMissionsResponse(missions, missions.size))

        } catch (e: Exception) {
            logger.error("멤버 미션 조회 실패: ${e.message}", e)
            ResponseEntity.internalServerError().build()
        }
    }
}
