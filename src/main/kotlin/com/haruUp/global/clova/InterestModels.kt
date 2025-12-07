package com.haruUp.global.clova

/**
 * 하나의 관심사 경로를 나타냄
 * 대분류만 있을 수도, 대분류>중분류만 있을 수도, 전체 경로가 있을 수도 있음
 */
data class InterestPath(
    val mainCategory: String,           // 대분류 (필수)
    val middleCategory: String? = null, // 중분류 (선택)
    val subCategory: String? = null     // 소분류 (선택)
) {
    /**
     * 현재 경로의 깊이 반환
     * 1: 대분류만, 2: 중분류까지, 3: 소분류까지
     */
    fun depth(): Int = when {
        subCategory != null -> 3
        middleCategory != null -> 2
        else -> 1
    }

    /**
     * 경로를 문자열로 표현
     */
    fun toPathString(): String = listOfNotNull(
        mainCategory,
        middleCategory,
        subCategory
    ).joinToString(" > ")

    /**
     * 다음 레벨이 무엇인지 반환
     * 1: 중분류 추천 필요, 2: 소분류 추천 필요, 3: 완성된 경로
     */
    fun nextLevel(): String = when (depth()) {
        1 -> "중분류"
        2 -> "소분류"
        else -> "완성"
    }
}

/**
 * 사용자 프로필 정보
 */
data class UserProfile(
    val age: Int? = null,
    val gender: String? = null,
    val occupation: String? = null,
    val existingInterests: List<String>? = null
) {
    /**
     * 프로필을 문자열로 표현 (AI 프롬프트용)
     */
    fun toProfileString(): String {
        val parts = mutableListOf<String>()
        age?.let { parts.add("${it}세") }
        gender?.let { parts.add(it) }
        occupation?.let { parts.add(it) }

        return if (parts.isEmpty()) "정보 없음" else parts.joinToString(", ")
    }
}

/**
 * 사용자의 전체 관심사 목록
 */
data class UserInterests(
    val interests: List<InterestPath>
) {
    /**
     * 특정 대분류에 속한 관심사들만 필터링
     */
    fun filterByMainCategory(mainCategory: String): List<InterestPath> {
        return interests.filter { it.mainCategory == mainCategory }
    }

    /**
     * 모든 대분류 목록 반환 (중복 제거)
     */
    fun getMainCategories(): List<String> {
        return interests.map { it.mainCategory }.distinct()
    }

    /**
     * 완전한 경로(소분류까지 선택된) 목록만 반환
     */
    fun getCompleteInterests(): List<InterestPath> {
        return interests.filter { it.depth() == 3 }
    }

    /**
     * 관심사 경로를 문자열 리스트로 변환
     */
    fun toPathStrings(): List<String> {
        return interests.map { it.toPathString() }
    }
}
