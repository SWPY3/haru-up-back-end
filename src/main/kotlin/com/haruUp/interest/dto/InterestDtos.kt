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

            예시:
            - MIDDLE 추천: [{"interestId": 1, "directFullPath": ["체력관리 및 운동"]}, {"interestId": 2, "directFullPath": ["외국어 공부"]}]
            - SUB 추천: [{"interestId": 1, "directFullPath": ["체력관리 및 운동", "헬스"]}, {"interestId": 2, "directFullPath": ["외국어 공부", "영어"]}]
        """,
        example = """[{"interestId": 1, "directFullPath": ["체력관리 및 운동", "헬스", "근력 키우기"]}]""",
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

    @Schema(description = "RAG(Vector DB 검색)로 추천한 개수", example = "7")
    val ragCount: Int,

    @Schema(description = "AI(Clova API)로 추천한 개수", example = "3")
    val aiCount: Int,

    @Schema(description = "총 추천 개수", example = "10")
    val totalCount: Int,

    @Schema(description = "RAG 추천 비율 (0.0 ~ 1.0)", example = "0.7")
    val ragRatio: Double,

    @Schema(description = "AI 추천 비율 (0.0 ~ 1.0)", example = "0.3")
    val aiRatio: Double,

    @Schema(description = "하이브리드 스코어링 사용 여부 (유사도 + 인기도)", example = "false")
    val usedHybridScoring: Boolean = false
) {
    val summary: String
        get() {
            val scoringMode = if (usedHybridScoring) "하이브리드 스코어링" else "유사도 스코어링"
            return "총 ${totalCount}개 (RAG: ${ragCount}개 ${(ragRatio * 100).toInt()}%, AI: ${aiCount}개 ${(aiRatio * 100).toInt()}%) [$scoringMode]"
        }
}

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
    val directFullPath: List<String>
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
 * 멤버가 선택한 관심사 정보 (vector 제외)
 */
@Schema(description = "멤버가 선택한 관심사 정보")
data class MemberInterestDto(
    @Schema(description = "관심사 ID", example = "1")
    val id: String,

    @Schema(description = "관심사 이름", example = "헬스")
    val name: String,

    @Schema(description = "관심사 레벨", allowableValues = ["MAIN", "MIDDLE", "SUB"], example = "MIDDLE")
    val level: String,

    @Schema(description = "부모 관심사 ID", example = "1")
    val parentId: String? = null,

    @Schema(description = "전체 경로 배열", example = "[\"체력관리 및 운동\", \"헬스\", \"근력 키우기\"]")
    val fullPath: List<String>,

    @Schema(description = "사용 횟수", example = "15")
    val usageCount: Int,

    @Schema(description = "활성화 여부", example = "true")
    val isActivated: Boolean
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