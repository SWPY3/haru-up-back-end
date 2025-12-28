package com.haruUp.ranking.controller

import com.haruUp.global.common.ApiResponse
import com.haruUp.member.domain.type.MemberGender
import com.haruUp.ranking.domain.AgeGroup
import com.haruUp.ranking.dto.PopularMissionResponse
import com.haruUp.ranking.dto.RankingFilterRequest
import com.haruUp.ranking.service.RankingQueryService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.slf4j.LoggerFactory
import org.springframework.batch.core.Job
import org.springframework.batch.core.JobParametersBuilder
import org.springframework.batch.core.launch.JobLauncher
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.LocalDate

@Tag(name = "미션 인기 랭킹 API", description = "미션 인기차트 조회 및 배치 관리")
@RestController
@RequestMapping("/api/ranking")
class RankingController(
    private val rankingQueryService: RankingQueryService,
    private val jobLauncher: JobLauncher,
    @Qualifier("rankingMissionDailyJob") private val rankingMissionDailyJob: Job
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 인기차트 조회 API
     */
    @Operation(
        summary = "미션 인기차트 조회",
        description = """
            필터 조건에 따라 인기 미션 랭킹을 조회합니다. (다중 선택 지원)

            **필터 조건:**
            - gender: 성별 (MALE, FEMALE)
            - ageGroups: 연령대 (다중 선택 가능)
            - jobIds: 직업 ID (다중 선택 가능)
            - jobDetailIds: 세부직업 ID (다중 선택 가능)
            - interests: 관심사 대분류 (다중 선택 가능)

            **호출 예시:**
            ```
            GET /api/ranking/popular
            GET /api/ranking/popular?gender=FEMALE
            GET /api/ranking/popular?ageGroups=MID_20S&ageGroups=LATE_20S
            GET /api/ranking/popular?jobIds=1&jobIds=2&jobDetailIds=3
            GET /api/ranking/popular?interests=외국어 공부&interests=운동
            ```
        """
    )
    @GetMapping("/popular")
    fun getPopularMissions(
        @Parameter(description = "성별 (MALE, FEMALE)")
        @RequestParam(required = false) gender: MemberGender?,

        @Parameter(description = "연령대 (다중 선택 가능: TEEN, EARLY_20S, MID_20S, LATE_20S, EARLY_30S, MID_30S, LATE_30S, FORTIES, FIFTIES_PLUS)")
        @RequestParam(required = false) ageGroups: List<AgeGroup>?,

        @Parameter(description = "직업 ID (다중 선택 가능)")
        @RequestParam(required = false) jobIds: List<Long>?,

        @Parameter(description = "세부직업 ID (다중 선택 가능)")
        @RequestParam(required = false) jobDetailIds: List<Long>?,

        @Parameter(description = "관심사 대분류 (다중 선택 가능)")
        @RequestParam(required = false) interests: List<String>?,

        @Parameter(description = "조회 개수 (기본값: 10)")
        @RequestParam(required = false, defaultValue = "10") limit: Int
    ): ResponseEntity<ApiResponse<List<PopularMissionResponse>>> {
        logger.info("인기차트 조회 - gender: $gender, ageGroups: $ageGroups, jobIds: $jobIds, jobDetailIds: $jobDetailIds, interests: $interests")

        return try {
            val filter = RankingFilterRequest(
                gender = gender,
                ageGroups = ageGroups,
                jobIds = jobIds,
                jobDetailIds = jobDetailIds,
                interests = interests
            )

            val result = rankingQueryService.getPopularMissions(filter, limit)
            ResponseEntity.ok(ApiResponse.success(result))
        } catch (e: Exception) {
            logger.error("인기차트 조회 실패: ${e.message}", e)
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
     * 랭킹 배치 수동 실행 API
     */
    @Operation(
        summary = "랭킹 배치 수동 실행",
        description = """
            랭킹 데이터 수집 배치를 수동으로 실행합니다.

            **처리 내용:**
            1. 선택된 미션 중 아직 ranking에 없는 것들 수집
            2. 라벨이 없으면 임베딩 유사도/LLM으로 생성
            3. ranking_mission_daily 테이블에 저장

            **호출 예시:**
            ```
            POST /api/ranking/batch
            POST /api/ranking/batch?targetDate=2025-12-28
            ```
        """
    )
    @PostMapping("/batch")
    fun executeBatchJob(
        @Parameter(description = "처리 대상 날짜 (기본값: 오늘)")
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        targetDate: LocalDate?
    ): ResponseEntity<ApiResponse<Map<String, Any>>> {
        val date = targetDate ?: LocalDate.now()
        logger.info("랭킹 Spring Batch Job 실행 - targetDate: $date")

        return try {
            val jobParameters = JobParametersBuilder()
                .addString("targetDate", date.toString())
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters()

            val jobExecution = jobLauncher.run(rankingMissionDailyJob, jobParameters)

            val result = mapOf(
                "jobId" to (jobExecution.jobId ?: -1),
                "status" to jobExecution.status.name,
                "startTime" to (jobExecution.startTime?.toString() ?: ""),
                "endTime" to (jobExecution.endTime?.toString() ?: ""),
                "exitStatus" to jobExecution.exitStatus.exitCode
            )

            ResponseEntity.ok(ApiResponse.success(result))
        } catch (e: Exception) {
            logger.error("랭킹 Spring Batch Job 실행 실패: ${e.message}", e)
            ResponseEntity.internalServerError().body(
                ApiResponse(
                    success = false,
                    data = null,
                    errorMessage = "배치 Job 실행 중 오류가 발생했습니다: ${e.message}"
                )
            )
        }
    }

    /**
     * 연령대 목록 조회
     */
    @Operation(
        summary = "연령대 목록 조회",
        description = "필터링에 사용 가능한 연령대 목록을 조회합니다."
    )
    @GetMapping("/age-groups")
    fun getAgeGroups(): ResponseEntity<ApiResponse<List<Map<String, String>>>> {
        val ageGroups = AgeGroup.entries.map {
            mapOf(
                "code" to it.name,
                "displayName" to it.displayName
            )
        }
        return ResponseEntity.ok(ApiResponse.success(ageGroups))
    }
}
