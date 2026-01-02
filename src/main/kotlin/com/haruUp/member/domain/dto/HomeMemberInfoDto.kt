package com.haruUp.member.domain.dto


data class HomeMemberInfoDto(
    val totalExp: Long,
    val currentExp: Long,
    val levelNumber: Int,
    val nickname: String,
    val interests: List<List<String>?> = emptyList()
) {
    // ⭐ JPQL 전용 생성자
    constructor(
        totalExp: Long,
        currentExp: Long,
        levelNumber: Int,
        nickname: String
    ) : this(
        totalExp = totalExp,
        currentExp = currentExp,
        levelNumber = levelNumber,
        nickname = nickname,
        interests = emptyList()
    )
}