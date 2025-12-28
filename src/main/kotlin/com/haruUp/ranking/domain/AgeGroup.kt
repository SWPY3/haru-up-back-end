package com.haruUp.ranking.domain

/**
 * 연령대 그룹
 * 인기차트 필터링에 사용
 */
enum class AgeGroup(val displayName: String, val minAge: Int, val maxAge: Int) {
    TEEN("10대", 10, 19),
    EARLY_20S("20~22세", 20, 22),
    MID_20S("23~26세", 23, 26),
    LATE_20S("27~29세", 27, 29),
    EARLY_30S("30~33세", 30, 33),
    MID_30S("33~36세", 33, 36),
    LATE_30S("37~39세", 37, 39),
    FORTIES("40대", 40, 49),
    FIFTIES_PLUS("50대 이상", 50, 999);

    companion object {
        fun fromAge(age: Int?): AgeGroup? {
            if (age == null) return null
            return entries.find { age in it.minAge..it.maxAge }
        }
    }
}
