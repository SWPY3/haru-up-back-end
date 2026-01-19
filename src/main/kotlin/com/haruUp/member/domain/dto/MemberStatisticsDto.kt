package com.haruUp.member.domain.dto

import java.time.LocalDateTime

data class MemberStatisticsDto(
    val snsId : String,
    val name : String,
    val levelNumber : Int,
    val characterId : Long,
    val createdAt : String
) {
    constructor(
        snsId : String,
        name : String,
        levelNumber : Int,
        characterId : Long,
        createdAt : LocalDateTime
    ) : this(
        snsId = snsId,
        name = name,
        levelNumber = levelNumber,
        characterId = characterId,
        createdAt = createdAt.toString()
    )
}