package com.haruUp.interest.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

/**
 * ================================
 * 관심사 모델
 * ================================
 */

/**
 * 관심사 계층 레벨
 */
enum class InterestLevel(val description: String) {
    MAIN("대분류"),
    MIDDLE("중분류"),
    SUB("소분류");

    fun next(): InterestLevel? = when (this) {
        MAIN -> MIDDLE
        MIDDLE -> SUB
        SUB -> null
    }
}

/**
 * 관심사 노드
 */
data class InterestNode(
    val id: String,
    var name: String,
    val level: InterestLevel,
    val parentId: String? = null,
    val fullPath: List<String>,
    var isEmbedded: Boolean = false,
    val isUserGenerated: Boolean = false,
    var usageCount: Int = 0,
    val createdBy: Long? = null,
    val createdAt: LocalDateTime? = null,
    var embeddedAt: LocalDateTime? = null
)

/**
 * 관심사 경로
 */
data class InterestPath(
    val mainCategory: String,
    val middleCategory: String? = null,
    val subCategory: String? = null
) {
    fun toPathString(): String = listOfNotNull(
        mainCategory,
        middleCategory,
        subCategory
    ).joinToString(" > ")

    fun toPathList(): List<String> = listOfNotNull(
        mainCategory,
        middleCategory,
        subCategory
    )
}

/**
 * ================================
 * 관심사 추천 요청/응답 DTO
 * ================================
 */

/**
 * 관심사 추천 요청
 */
@Schema(description = "관심사 추천 요청")
data class InterestRecommendationRequest(
    @Schema(
        description = """
            선택한 관심사 경로 목록
            - 빈 배열: 처음 시작 (인기 관심사 추천)
            - 1개 이상: 선택한 관심사들을 기반으로 추천
            - interestId: 프론트엔드 추적용 관심사 ID
            - job_id, job_detail_id: 직업 정보 (선택, 있으면 직업 관련 추천)

            예시:
            - SUB 추천: [{"interestId": 18, "directFullPath": ["자격증 공부", "직무 전문 분야"], "job_id": 1, "job_detail_id": 17}]
        """,
        example = """[{"interestId": 18, "directFullPath": ["자격증 공부", "직무 전문 분야"], "job_id": 1, "job_detail_id": 17}]""",
        required = false
    )
    val category: List<InterestsDto> = emptyList(),

    @Schema(
        description = "추천받을 관심사 레벨",
        allowableValues = ["MAIN", "MIDDLE", "SUB"],
        example = "SUB",
        required = true
    )
    val currentLevel: String,

    @Schema(
        description = "추천받을 관심사 개수",
        example = "10",
        defaultValue = "10"
    )
    val targetCount: Int = 10
)

/**
 * 관심사 추천 응답
 */
@Schema(description = "관심사 추천 응답")
data class InterestRecommendationResponse(
    @Schema(description = "추천된 관심사 목록")
    val interests: List<Map<String, Any?>>,

    @Schema(description = "총 추천 개수", example = "10")
    val totalCount: Int
)

/**
 * ================================
 * 공통 DTO
 * ================================
 */

/**
 * 관심사 경로 DTO
 */
@Schema(description = "관심사 선택 경로")
data class InterestsDto(
    @Schema(
        description = "관심사 ID",
        example = "1",
        required = true
    )
    val interestId: Long,

    @Schema(
        description = "전체 경로 배열 [대분류, 중분류, 소분류]",
        example = """["체력관리 및 운동", "헬스", "근력 키우기"]""",
        required = true
    )
    val directFullPath: List<String>,

    @Schema(
        description = "직업 ID (선택)",
        example = "1",
        required = false
    )
    @com.fasterxml.jackson.annotation.JsonProperty("job_id")
    val jobId: Long? = null,

    @Schema(
        description = "직업 상세 ID (선택)",
        example = "2",
        required = false
    )
    @com.fasterxml.jackson.annotation.JsonProperty("job_detail_id")
    val jobDetailId: Long? = null
) {
    /**
     * directFullPath를 InterestPath 모델로 변환
     */
    fun toModel(): InterestPath {
        return InterestPath(
            mainCategory = directFullPath.getOrNull(0) ?: "",
            middleCategory = directFullPath.getOrNull(1),
            subCategory = directFullPath.getOrNull(2)
        )
    }
}

/**
 * ================================
 * 멤버 관심사/미션 조회 DTO
 * ================================
 */

/**
 * 멤버가 선택한 관심사 정보 (member_interest + interest_embeddings.full_path)
 */
@Schema(description = "멤버가 선택한 관심사 정보")
data class MemberInterestDto(
    @Schema(description = "멤버 관심사 ID (member_interest.id)", example = "1")
    val member_interest_id: Long,

    @Schema(description = "멤버 ID", example = "1")
    val memberId: Long,

    @Schema(description = "관심사 ID (interest_embeddings.id)", example = "64")
    val interestId: Long,

    @Schema(description = "직접 저장된 전체 경로 (member_interest.direct_full_path)", example = "[\"체력관리 및 운동\", \"헬스\", \"근력 키우기\"]")
    val directFullPath: List<String>?,

    @Schema(description = "미션 재추천 횟수", example = "0")
    val resetMissionCount: Int,

    @Schema(description = "생성일시", example = "2025-01-01T00:00:00")
    val createdAt: java.time.LocalDateTime?,

    @Schema(description = "수정일시", example = "2025-01-01T00:00:00")
    val updatedAt: java.time.LocalDateTime?,

    @Schema(description = "interest_embeddings의 전체 경로", example = "[\"체력관리 및 운동\", \"헬스\", \"근력 키우기\"]")
    val fullPath: List<String>?
)

/**
 * 멤버 관심사 조회 응답
 */
@Schema(description = "멤버 관심사 조회 응답")
data class MemberInterestsResponse(
    @Schema(description = "멤버가 선택한 관심사 목록")
    val interests: List<MemberInterestDto>,

    @Schema(description = "총 개수", example = "5")
    val totalCount: Int
)

/**
 * ================================
 * 시스템 관심사 조회 DTO
 * ================================
 */

/**
 * 관심사 데이터 정보
 */
@Schema(description = "관심사 데이터 정보")
data class InterestDataDto(
    @Schema(description = "관심사 ID", example = "1")
    val id: Long,

    @Schema(description = "부모 관심사 ID", example = "5")
    val parentId: Long?,

    @Schema(description = "관심사 레벨", allowableValues = ["MAIN", "MIDDLE", "SUB"], example = "MAIN")
    val level: String,

    @Schema(description = "관심사 이름", example = "체력관리 및 운동")
    val name: String,

    @Schema(description = "사용 횟수", example = "15")
    val usageCount: Int
)

/**
 * 시스템 관심사 조회 응답
 */
@Schema(description = "관심사 데이터 조회 응답")
data class InterestsDataResponse(
    @Schema(description = "관심사 데이터 목록")
    val interests: List<InterestDataDto>,

    @Schema(description = "총 개수", example = "50")
    val totalCount: Int
)


/**
 * 멤버 관심사 저장 요청
 */
@Schema(description = "멤버 관심사 저장 요청")
data class MemberInterestSaveRequest(
    @Schema(
        description = """
            멤버 관심사 저장 요청
            예시:
            - 소분류까지 저장: [{"interestId": 64, "directFullPath": ["체력관리 및 운동", "헬스", "근력 키우기"]}]
        """,
        example = """[{"interestId": 64, "directFullPath": ["체력관리 및 운동", "헬스", "근력 키우기"]}]""",
        required = false
    )
    val interests: List<InterestsDto> = emptyList(),
)

/**
 * 멤버 관심사 수정 요청
 */
@Schema(description = "멤버 관심사 수정 요청")
data class MemberInterestUpdateRequest(
    @Schema(
        description = "새로운 관심사 ID",
        example = "64",
        required = true
    )
    val interestId: Long,

    @Schema(
        description = "새로운 전체 경로 배열 [대분류, 중분류, 소분류]",
        example = """["체력관리 및 운동", "헬스", "근력 키우기"]""",
        required = true
    )
    val directFullPath: List<String>
)