package com.haruUp.domain.mission.dto

import com.haruUp.interest.dto.InterestPathDto
import io.swagger.v3.oas.annotations.media.Schema

/**
 * ================================
 * 미션 추천 요청/응답 DTO
 * ================================
 */

/**
 * 미션 추천 요청
 */
@Schema(description = "미션 추천 요청")
data class MissionRecommendationRequest(
    @Schema(
        description = "사용자 ID",
        example = "1",
        required = true
    )
    val userId: Long,

    @Schema(
        description = "사용자가 선택한 관심사 목록 (대분류 > 중분류 > 소분류, 난이도 포함)",
        example = """[
            {"seqNo": 1, "mainCategory": "체력관리 및 운동", "middleCategory": "헬스", "subCategory": "근력 키우기", "difficulty": 1},
            {"seqNo": 2, "mainCategory": "외국어 공부", "middleCategory": "영어", "subCategory": "단어 학습", "difficulty": 2}
        ]""",
        required = true
    )
    val interests: List<InterestPathDto>
)

/**
 * 미션 추천 응답
 */
@Schema(description = "미션 추천 응답")
data class MissionRecommendationResponse(
    @Schema(description = "추천된 미션 목록")
    val missions: List<MissionDto>,

    @Schema(description = "총 추천된 미션 개수", example = "10")
    val totalCount: Int
)

/**
 * 미션 DTO
 */
@Schema(description = "미션 정보")
data class MissionDto(
    @Schema(description = "순번 (어떤 관심사에 대한 미션인지)", example = "1")
    val seqNo: Int?,

    @Schema(description = "미션 내용", example = "주 3회 가슴 운동 루틴 완수하기")
    val content: String,

    @Schema(description = "관련 관심사 경로", example = "체력관리 및 운동 > 헬스 > 근력 키우기")
    val relatedInterest: String,

    @Schema(description = "난이도 (1~5, null이면 난이도 미설정)", example = "3")
    val difficulty: Int?
)

/**
 * ================================
 * 미션 선택 요청/응답 DTO
 * ================================
 */

/**
 * 미션 선택 아이템
 */
@Schema(description = "선택한 미션 정보")
data class SelectedMissionDto(
    @Schema(
        description = "부모 관심사 ID (interest_embeddings 테이블의 ID)",
        example = "97",
        required = true
    )
    val parentId: Long,

    @Schema(
        description = "전체 경로 배열 [대분류, 중분류, 소분류]",
        example = """["직무 관련 역량 개발", "업무 능력 향상", "문서·기획·정리 스킬 향상(PPT·보고서)"]""",
        required = true
    )
    val directFullPath: List<String>,

    @Schema(
        description = "난이도 (1~5, 선택)",
        allowableValues = ["1", "2", "3", "4", "5"],
        example = "1",
        required = false
    )
    val difficulty: Int? = null,

    @Schema(
        description = "미션 내용",
        example = "보고서 작성법 관련 책 1권 읽고 요약 정리하기",
        required = true
    )
    val mission: String
)

/**
 * 미션 선택 요청
 */
@Schema(description = "미션 선택 요청")
data class MissionSelectionRequest(
    @Schema(
        description = "사용자 ID",
        example = "1",
        required = true
    )
    val userId: Long,

    @Schema(
        description = "선택한 미션 목록",
        example = """[
            {
                "parentId": 97,
                "directFullPath": ["직무 관련 역량 개발", "업무 능력 향상", "문서·기획·정리 스킬 향상(PPT·보고서)"],
                "difficulty": 1,
                "mission": "보고서 작성법 관련 책 1권 읽고 요약 정리하기"
            }
        ]""",
        required = true
    )
    val missions: List<SelectedMissionDto>
)

/**
 * 미션 선택 응답
 */
@Schema(description = "미션 선택 응답")
data class MissionSelectionResponse(
    @Schema(description = "저장된 미션 개수", example = "2")
    val savedCount: Int,

    @Schema(description = "저장된 미션 ID 목록", example = "[1, 2]")
    val missionIds: List<Long>
)
