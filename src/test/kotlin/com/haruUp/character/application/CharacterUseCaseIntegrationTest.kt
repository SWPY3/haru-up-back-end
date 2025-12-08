package com.haruUp.character.application

import com.haruUp.character.application.service.LevelService
import com.haruUp.character.application.service.MemberCharacterService
import com.haruUp.character.domain.Level
import com.haruUp.character.domain.MemberCharacter
import com.haruUp.character.infrastructure.LevelRepository
import com.haruUp.character.infrastructure.MemberCharacterRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@Transactional   // 테스트마다 롤백
class CharacterUseCaseIntegrationTest @Autowired constructor(
    private val characterUseCase: CharacterUseCase,
    private val memberCharacterRepository: MemberCharacterRepository,
    private val levelRepository: LevelRepository,
    private val memberCharacterService: MemberCharacterService,
    private val levelService: LevelService
) {

    @BeforeEach
    fun cleanUp() {
        memberCharacterRepository.deleteAll()
        levelRepository.deleteAll()
    }

    @Test
    fun `경험치가 threshold를 넘으면 실제 DB에서 레벨업이 일어난다`() {
        // --- GIVEN ---
        val level1 = levelRepository.save(Level(
            id = null,
            levelNumber = 1,
            requiredExp = 100
        ))

        val level2 = levelRepository.save(Level(
            id = null,
            levelNumber = 2,
            requiredExp = 200
        ))

        val mc = memberCharacterRepository.save(
            MemberCharacter(
                id = null,
                memberId = 10L,
                characterId = 3L,
                levelId = level1.id!!,
                totalExp = 0,
                currentExp = 0
            )
        )

        val expEarned = 150  // → level1.requiredExp(100)을 넘기 때문에 level2로 올라가야 함

        // --- WHEN ---
        val updated = characterUseCase.applyMissionExp(mc.memberId, expEarned)

        // --- THEN ---
        val mcFromDb = memberCharacterRepository.findById(updated.id!!).get()

        assertEquals(level2.id!!, mcFromDb.levelId)    // 레벨업 확인
        assertEquals(150, mcFromDb.totalExp)           // 총 경험치 증가
        assertEquals(50, mcFromDb.currentExp)          // 150 - 100 = 50 남음
    }

    @Test
    fun `nextLevel이 없으면 최고 레벨로 처리되고 DB도 변화 없다`() {
        // --- GIVEN ---
        val level3 = levelRepository.save(Level(
            id = null,
            levelNumber = 3,
            requiredExp = 200
        ))

        val mc = memberCharacterRepository.save(
            MemberCharacter(
                id = null,
                memberId = 77L,
                characterId = 2L,
                levelId = level3.id!!,
                totalExp = 0,
                currentExp = 0
            )
        )

        val expEarned = 200    // 최고 레벨이므로 level 유지해야 함

        // WHEN
        val updated = characterUseCase.applyMissionExp(mc.memberId, expEarned)

        // THEN
        val mcFromDb = memberCharacterRepository.findById(updated.id!!).get()

        assertEquals(level3.id!!, mcFromDb.levelId)     // 레벨 변함 없음
        assertEquals(200, mcFromDb.totalExp)
        assertEquals(200, mcFromDb.currentExp)
    }

    @Test
    fun `선택된 캐릭터가 없으면 IllegalStateException`() {
        // WHEN & THEN
        assertThrows(IllegalStateException::class.java) {
            characterUseCase.applyMissionExp(999L, 100)
        }
    }
}
