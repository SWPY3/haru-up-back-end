package com.haruUp.member.domain.dto

import com.haruUp.character.domain.dto.MemberCharacterDto


data class HomeMemberInfoDto(
    val characterId: Long,
    val totalExp: Long,
    val currentExp: Long,
    val maxExp: Int?,
    val levelNumber: Int,
    val nickname: String,
    val interests: List<List<String>?> = emptyList()
) {
    // ⭐ JPQL 전용 생성자
    constructor(
        characterId : Long,
        totalExp: Long,
        currentExp: Long,
        maxExp: Int?,
        levelNumber: Int,
        nickname: String
    ) : this(
        characterId = characterId,
        totalExp = totalExp,
        currentExp = currentExp,
        maxExp = maxExp,
        levelNumber = levelNumber,
        nickname = nickname,
        interests = emptyList()
    )
}