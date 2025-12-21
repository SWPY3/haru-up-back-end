package com.haruUp.character.application.service

import com.haruUp.character.domain.Character
import com.haruUp.character.infrastructure.CharacterRepository
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service

@Service
class CharacterService(
    private var characterRepository : CharacterRepository
) {

    @Transactional
    fun validateExists(characterId: Long) {
        if (!characterRepository.existsById(characterId)) {
            throw IllegalArgumentException("Character not found: $characterId")
        }
    }

    @Transactional
    fun getById(characterId: Long): Character? {
        return characterRepository.findById(characterId)
            .orElseThrow { IllegalArgumentException("Character not found: $characterId") }
    }

    @Transactional
    fun getCharacterList()  : List<Character>{
        return characterRepository.findAll();
    }
}