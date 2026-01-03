package com.haruUp.member.domain.dto


data class HomeMemberInfoDto(
    val totalExp: Long,
    val currentExp: Long,
    val maxExp: Int?,
    val levelNumber: Int,
    val nickname: String,
    val interests: List<List<String>?> = emptyList()
) {
    // ⭐ JPQL 전용 생성자
    constructor(
        totalExp: Long,
        currentExp: Long,
        maxExp: Int?,
        levelNumber: Int,
        nickname: String
    ) : this(
        totalExp = totalExp,
        currentExp = currentExp,
        maxExp = maxExp,
        levelNumber = levelNumber,
        nickname = nickname,
        interests = emptyList()
    )
}