package com.haruUp.character.domain.dto

import com.haruUp.character.domain.Level
import com.haruUp.global.common.BaseEntity

class LevelDto(
    var id: Long? = null,

    var levelNumber: Int,            // 1, 2, 3…
    var requiredExp: Int,            // 해당 레벨 → 다음 레벨로 가기 위한 필요 경험치
    var maxExp: Int? = null,         // 선택: 레벨별 최대 경험치

) : BaseEntity() {

    fun toEntity(): Level = Level(
        id = this.id,
        levelNumber = this.levelNumber,
        requiredExp = this.requiredExp,
        maxExp = this.maxExp
    )
}