package com.haruUp.interest.model

import java.time.LocalDateTime

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
 *
 * 대분류, 중분류, 소분류 모두 이 구조 사용
 */
data class InterestNode(
    val id: String,                          // 고유 ID
    var name: String,                        // 관심사 이름
    val level: InterestLevel,                // 계층 레벨
    val parentId: String? = null,            // 부모 ID
    val fullPath: List<String>,              // 전체 경로 배열 (예: ["운동", "헬스", "가슴 운동"])
    var isEmbedded: Boolean = false,         // 임베딩 여부
    val isUserGenerated: Boolean = false,    // 사용자가 직접 입력했는지
    var usageCount: Int = 0,                 // 사용 횟수
    val createdBy: Long? = null,             // 생성한 사용자 ID
    val createdAt: LocalDateTime,
    var embeddedAt: LocalDateTime? = null
)


/**
 * 관심사 경로
 */
data class InterestPath(
    val mainCategory: String,               // 대분류
    val middleCategory: String? = null,     // 중분류
    val subCategory: String? = null         // 소분류
) {
    /**
     * 경로를 문자열로 표현
     */
    fun toPathString(): String = listOfNotNull(
        mainCategory,
        middleCategory,
        subCategory
    ).joinToString(" > ")

    /**
     * 경로를 리스트로 표현
     */
    fun toPathList(): List<String> = listOfNotNull(
        mainCategory,
        middleCategory,
        subCategory
    )
}

/**
 * 사용자의 여러 관심사
 */
data class UserInterests(
    val interests: List<InterestPath>
) {
    fun toPathStrings(): List<String> {
        return interests.map { it.toPathString() }
    }
}
