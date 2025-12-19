package com.haruUp.interest.dto

import com.haruUp.interest.model.InterestLevel
import com.haruUp.interest.model.InterestNode
import com.haruUp.interest.model.InterestPath
import io.swagger.v3.oas.annotations.media.Schema

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
            - seqNo: 프론트엔드 추적용 순번 (선택)

            예시:
            - MIDDLE 추천: [{"seqNo": 1, "directFullPath": ["체력관리 및 운동"]}, {"seqNo": 2, "directFullPath": ["외국어 공부"]}]
            - SUB 추천: [{"seqNo": 1, "directFullPath": ["체력관리 및 운동", "헬스"]}, {"seqNo": 2, "directFullPath": ["외국어 공부", "영어"]}]
        """,
        example = """[{"seqNo": 1, "directFullPath": ["체력관리 및 운동", "헬스"]}, {"seqNo": 2, "directFullPath": ["외국어 공부", "영어"]}]""",
        required = false
    )
    val category: List<InterestPathDto> = emptyList(),

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
    val interests: List<InterestNodeDto>,

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
data class InterestPathDto(
    @Schema(
        description = "순번 (프론트엔드 추적용, 선택)",
        example = "1",
        required = false
    )
    val seqNo: Int? = null,

    @Schema(
        description = "전체 경로 배열 [대분류, 중분류, 소분류] (directFullPath 사용 시 mainCategory, middleCategory, subCategory 무시)",
        example = """["체력관리 및 운동", "헬스", "근력 키우기"]""",
        required = false
    )
    val directFullPath: List<String>,

    @Schema(
        description = "난이도 (1~5, 선택)",
        allowableValues = ["1", "2", "3", "4", "5"],
        example = "3",
        required = false
    )
    val difficulty: Int? = null
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

    /**
     * 카테고리 값 조회
     */
    fun resolveMainCategory(): String? = directFullPath.getOrNull(0)
    fun resolveMiddleCategory(): String? = directFullPath.getOrNull(1)
    fun resolveSubCategory(): String? = directFullPath.getOrNull(2)
}

/**
 * 관심사 노드 DTO
 */
@Schema(description = "관심사 상세 정보")
data class InterestNodeDto(
    @Schema(description = "관심사 ID", example = "1")
    val id: String,

    @Schema(description = "관심사 이름", example = "헬스")
    val name: String,

    @Schema(
        description = "관심사 레벨",
        allowableValues = ["MAIN", "MIDDLE", "SUB"],
        example = "MIDDLE"
    )
    val level: String,

    @Schema(description = "부모 관심사 ID", example = "1")
    val parentId: String? = null,

    @Schema(description = "임베딩 여부 (RAG 데이터 존재)", example = "true")
    val isEmbedded: Boolean,

    @Schema(description = "사용 횟수 (인기도)", example = "15")
    val usageCount: Int,

    @Schema(description = "전체 경로 배열", example = "[\"체력관리 및 운동\", \"헬스\"]")
    val fullPath: List<String>,

    @Schema(description = "순번 (요청한 category의 seqNo)", example = "1")
    val seqNo: Int? = null
) {
    companion object {
        fun from(node: InterestNode, seqNo: Int? = null): InterestNodeDto {
            return InterestNodeDto(
                id = node.id,
                name = node.name,
                level = node.level.name,
                parentId = node.parentId,
                isEmbedded = node.isEmbedded,
                usageCount = node.usageCount,
                fullPath = node.fullPath,
                seqNo = seqNo
            )
        }
    }
}

/**
 * ================================
 * 미션 완료 알림 DTO
 * ================================
 */

/**
 * 미션 완료 알림 요청
 */
data class MissionCompletedRequest(
    val userId: Long,
    val interestPath: InterestPathDto
)

/**
 * ================================
 * 임베딩 초기화 응답 DTO
 * ================================
 */

/**
 * 임베딩 초기화 응답
 */
@Schema(description = "임베딩 초기화 결과")
data class EmbeddingInitResponseDto(
    @Schema(description = "성공한 개수", example = "95")
    val successCount: Int,

    @Schema(description = "실패한 개수", example = "2")
    val failCount: Int,

    @Schema(description = "스킵한 개수 (이미 임베딩된 항목)", example = "3")
    val skipCount: Int,

    @Schema(description = "총 처리한 개수", example = "97")
    val totalProcessed: Int,

    @Schema(description = "소요 시간 (초)", example = "15.32")
    val elapsedSeconds: Double,

    @Schema(description = "결과 요약 메시지")
    val summary: String
)

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

    @Schema(description = "전체 경로 배열", example = "[\"체력관리 및 운동\", \"헬스\"]")
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
            - 소분류까지 저장: [{"parentId": 1, "directFullPath": ["체력관리 및 운동", "헬스", "근력 키우기"]}]
        """,
        example = """[{"parentId": 1, "directFullPath": ["체력관리 및 운동", "헬스", "근력 키우기"]}]""",
        required = false
    )
    val interests: List<InterestPathDto> = emptyList(),
)