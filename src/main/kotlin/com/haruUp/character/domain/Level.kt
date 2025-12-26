package com.haruUp.character.domain

import com.haruUp.character.domain.dto.LevelDto
import com.haruUp.global.common.BaseEntity
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import lombok.NoArgsConstructor

@Entity
@NoArgsConstructor
class Level(

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    var levelNumber: Int,            // 1, 2, 3…
    var requiredExp: Int,            // 해당 레벨 → 다음 레벨로 가기 위한 필요 경험치
    var maxExp: Int ?= null,         // 선택: 레벨별 최대 경험치

) : BaseEntity() {
    fun toDto() : LevelDto = LevelDto(

        id = this.id,
        levelNumber = this.levelNumber,
        requiredExp = this.requiredExp,
        maxExp = this.maxExp,
    )
}