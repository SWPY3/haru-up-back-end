package com.haruUp.character.application.service

import com.haruUp.character.domain.Character
import com.haruUp.character.infrastructure.CharacterRepository
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service

@Service
class CharacterService(
    private var characterRepository : CharacterRepository
) {

    /** 캐릭터 존재 여부를 검증한다. */
    @Transactional
    fun validateExists(characterId: Long) {
        if (!characterRepository.existsById(characterId)) {
            throw IllegalArgumentException("Character not found: $characterId")
        }
    }

    /** 캐릭터 ID로 캐릭터를 조회한다. */
    @Transactional
    fun getById(characterId: Long): Character? {
        return characterRepository.findById(characterId)
            .orElseThrow { IllegalArgumentException("Character not found: $characterId") }
    }

    /** 전체 캐릭터 목록을 조회한다. */
    @Transactional
    fun getCharacterList()  : List<Character>{
        return characterRepository.findAll();
    }
}
