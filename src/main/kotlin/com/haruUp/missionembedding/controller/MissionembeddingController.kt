package com.haruUp.missionembedding.controller

import com.haruUp.global.common.ApiResponse as CommonApiResponse
import com.haruUp.global.security.MemberPrincipal
import org.springframework.security.core.annotation.AuthenticationPrincipal
import com.haruUp.missionembedding.dto.MissionRecommendationRequest
import com.haruUp.missionembedding.dto.MissionRecommendationResponse
import com.haruUp.missionembedding.dto.MissionSelectionRequest
import com.haruUp.missionembedding.service.MissionRecommendationService
import com.haruUp.missionembedding.service.MissionSelectionService
import com.haruUp.global.clova.MissionMemberProfile
import com.haruUp.global.ratelimit.RateLimit
import com.haruUp.member.infrastructure.MemberProfileRepository
import com.haruUp.category.repository.JobRepository
import com.haruUp.category.repository.JobDetailRepository
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
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
    private val memberProfileRepository: MemberProfileRepository,
    private val jobRepository: JobRepository,
    private val jobDetailRepository: JobDetailRepository
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
