package com.haruUp.character.application.service

import com.haruUp.character.domain.Level
import com.haruUp.character.domain.MemberCharacter
import com.haruUp.character.domain.dto.LevelDto
import com.haruUp.character.domain.dto.MemberCharacterDto
import com.haruUp.character.infrastructure.MemberCharacterRepository
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service
import java.time.LocalDate
import kotlin.math.max

@Service
class MemberCharacterService(
    private val memberCharacterRepository: MemberCharacterRepository
) {

    @Transactional
    fun createInitial(memberId: Long, characterId: Long, levelId: Long): MemberCharacter {

        val mc = MemberCharacter(
            memberId = memberId,
            characterId = characterId,
            levelId = levelId
        )

        return memberCharacterRepository.save(mc)
    }

    @Transactional
    fun getSelectedCharacter(memberId: Long): MemberCharacter? {
        return memberCharacterRepository.findByMemberId(memberId)
    }

    @Transactional
    fun applyExpWithResolvedValues(
        mc: MemberCharacter,
        newLevelId: Long,
        totalExp: Int,
        currentExp: Int
    ): MemberCharacter {

        mc.levelId = newLevelId
        mc.totalExp = totalExp
        mc.currentExp = currentExp

        // streak, mission count 등 업데이트
        mc.totalMissions += 1
        mc.completedMissions += 1

        val today = LocalDate.now()
        val yesterday = today.minusDays(1)

        if (mc.lastMissionDate == yesterday) {
            mc.currentStreakDays += 1
        } else {
            mc.currentStreakDays = 1
        }

        mc.longestStreakDays = max(mc.longestStreakDays, mc.currentStreakDays)

        mc.lastMissionDate = today

        return memberCharacterRepository.save(mc)
    }
}