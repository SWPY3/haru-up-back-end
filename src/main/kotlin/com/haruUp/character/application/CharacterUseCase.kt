package com.haruUp.character.application

import com.haruUp.character.application.service.CharacterService
import com.haruUp.character.application.service.LevelService
import com.haruUp.character.application.service.MemberCharacterService
import com.haruUp.character.domain.dto.CharacterDto
import com.haruUp.character.domain.dto.MemberCharacterDto
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service

@Service
class CharacterUseCase(
    private val memberCharacterService: MemberCharacterService,
    private val levelService: LevelService,
    private val characterService: CharacterService
) {

    /** 사용 가능한 캐릭터 목록을 조회한다. */
    @Transactional
    fun characterList(): List<CharacterDto> =
        characterService.getCharacterList().map { it.toDto() }

    /** 회원의 초기 캐릭터/레벨 정보를 생성한다. */
    @Transactional
    fun createInitialCharacter(memberId: Long, characterId: Long): MemberCharacterDto {

        // 1. 캐릭터 존재 여부 확인
        characterService.validateExists(characterId)

        // 2. 초기 레벨 조회
        val levelId = levelService.getInitialLevelId()

        // 3. MemberCharacter 생성
        val mc = memberCharacterService.createInitial(memberId, characterId, levelId)

        return mc.toDto()
    }

}
