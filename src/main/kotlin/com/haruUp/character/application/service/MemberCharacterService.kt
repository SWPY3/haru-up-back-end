package com.haruUp.character.application.service

import com.haruUp.character.domain.Level
import com.haruUp.character.domain.MemberCharacter
import com.haruUp.character.domain.dto.LevelDto
import com.haruUp.character.domain.dto.MemberCharacterDto
import com.haruUp.character.infrastructure.MemberCharacterRepository
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service

@Service
class MemberCharacterService(
    private val repo: MemberCharacterRepository
) {

    fun createInitial(memberId: Long, characterId: Long, levelId: Long): MemberCharacter {

        val mc = MemberCharacter(
            memberId = memberId,
            characterId = characterId,
            levelId = levelId
        )

        return repo.save(mc)
    }

    fun getSelectedCharacter(memberId: Long) : MemberCharacter {
        TODO("Not yet implemented")

    }

    @Transactional
    fun applyExp(
        mc: MemberCharacter,
        expEarned: Int,
        currentLevel: Level,
        nextLevel: Level?
    ): MemberCharacter {

        mc.totalExp += expEarned
        mc.currentExp += expEarned

        // 레벨업 조건 확인
        while (nextLevel != null && mc.currentExp >= currentLevel.requiredExp) {

            // 경험치 차감
            mc.currentExp -= currentLevel.requiredExp

            // 레벨 업
            mc.levelId = nextLevel.id!!

            // 다음 레벨 갱신
            val newLevel = nextLevel
            val newNext = null // 최고레벨이면 null

            // 반복 여부 판단
            if (newNext == null) break
        }

        return repo.save(mc)
    }
}