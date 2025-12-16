package com.haruUp.missionembedding.dto

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
        description = "사용자가 선택한 관심사 목록 (대분류 > 중분류 > 소분류, 난이도 포함)",
        example = """[
            {"seqNo": 1, "directFullPath": ["체력관리 및 운동", "헬스", "근력 키우기"], "difficulty": 1},
            {"seqNo": 2, "directFullPath": ["외국어 공부", "영어", "단어 학습"], "difficulty": 2}
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
    @Schema(description = "관심사별 추천된 미션 그룹 목록")
    val missions: List<MissionGroupDto>,

    @Schema(description = "총 추천된 미션 개수", example = "10")
    val totalCount: Int
)

/**
 * 관심사별 미션 그룹 DTO
 */
@Schema(description = "관심사별 미션 그룹")
data class MissionGroupDto(
    @Schema(description = "순번 (어떤 관심사에 대한 미션인지)", example = "1")
    val seqNo: Int?,

    @Schema(description = "해당 관심사의 미션 목록")
    val data: List<MissionDto>
)

/**
 * 미션 DTO
 */
@Schema(description = "미션 정보")
data class MissionDto(
    @Schema(description = "mission_embeddings 테이블 ID", example = "123")
    val id: Long?,

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
        description = "mission_embeddings 테이블의 ID",
        example = "123",
        required = true
    )
    val missionId: Long
)

/**
 * 미션 선택 요청
 */
@Schema(description = "미션 선택 요청")
data class MissionSelectionRequest(
    @Schema(
        description = "선택한 미션 목록",
        example = """[
            {
                "parentId": 97,
                "directFullPath": ["직무 관련 역량 개발", "업무 능력 향상", "문서·기획·정리 스킬 향상(PPT·보고서)"],
                "difficulty": 1,
                "missionId": 123
            }
        ]""",
        required = true
    )
    val missions: List<SelectedMissionDto>
)
