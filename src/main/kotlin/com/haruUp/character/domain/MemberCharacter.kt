package com.haruUp.character.domain

import com.haruUp.character.domain.dto.MemberCharacterDto
import com.haruUp.global.common.BaseEntity
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import lombok.NoArgsConstructor
import java.time.LocalDate

@Entity
@NoArgsConstructor
class MemberCharacter (
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    var memberId: Long,
    var characterId: Long,
    var levelId: Long,                 // Level 엔티티 직접 참조 X → ID만 저장

    var totalExp: Int = 0,
    var currentExp: Int = 0,

    var completedMissions: Int = 0,
    var failedMissions: Int = 0,
    var totalMissions: Int = 0,

    var currentStreakDays: Int = 0,
    var longestStreakDays: Int = 0,

    var lastMissionDate: LocalDate? = null,

) : BaseEntity() {

    fun toDto() : MemberCharacterDto = MemberCharacterDto(
       id = this.id,
        memberId = this.memberId,
        characterId = this.characterId,
        levelId = this.levelId,
        totalExp = this.totalExp,
        currentExp = this.currentExp,
        completedMissions = this.completedMissions,
        failedMissions = this.failedMissions,
        totalMissions = this.totalMissions,
        currentStreakDays = this.currentStreakDays,
        longestStreakDays = this.longestStreakDays,
        lastMissionDate = this.lastMissionDate

    )
}