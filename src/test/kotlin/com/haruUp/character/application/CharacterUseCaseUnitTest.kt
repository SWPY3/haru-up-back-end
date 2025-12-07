package com.haruUp.character.application

import com.haruUp.character.application.service.CharacterService
import com.haruUp.character.application.service.LevelService
import com.haruUp.character.application.service.MemberCharacterService
import com.haruUp.character.domain.Level
import com.haruUp.character.domain.MemberCharacter
import com.haruUp.character.domain.dto.MemberCharacterDto
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@ExtendWith(MockitoExtension::class)
class CharacterUseCaseUnitTest {

    @Mock lateinit var memberCharacterService: MemberCharacterService
    @Mock lateinit var levelService: LevelService
    @Mock lateinit var characterService: CharacterService

    @InjectMocks lateinit var characterUseCase: CharacterUseCase

    private fun MemberCharacter.toDtoMock(): MemberCharacterDto =
        MemberCharacterDto(
            id = this.id!!,
            memberId = this.memberId,
            characterId = this.characterId,
            levelId = this.levelId,
            totalExp = this.totalExp,
            currentExp = this.currentExp
        )

    @BeforeEach
    fun setup() {
        // nothing required
    }

    // ---------------------------------------------------------
    // 1) 정상적인 경험치 반영 시 모든 서비스가 호출되는지 검증
    // ---------------------------------------------------------
    @Test
    fun `미션 경험치를 적용하면 서비스들이 올바르게 호출된다`() {
        val memberId = 100L
        val expEarned = 120

        val mc = MemberCharacter(
            id = 1L,
            memberId = memberId,
            characterId = 10L,
            levelId = 1L,
            totalExp = 0,
            currentExp = 0
        )

        val level1 = Level(id = 1L, levelNumber = 1, requiredExp = 100)
        val level2 = Level(id = 2L, levelNumber = 2, requiredExp = 200)

        whenever(memberCharacterService.getSelectedCharacter(memberId)).thenReturn(mc)
        whenever(levelService.getById(1L)).thenReturn(level1)
        whenever(levelService.getNextLevel(1)).thenReturn(level2)

        // applyExp 결과는 변경된 MemberCharacter 를 그대로 반환
        doReturn(mc).`when`(memberCharacterService)
            .applyExp(any(), any(), any(), any())

        val result = characterUseCase.applyMissionExp(memberId, expEarned)

        assertNotNull(result)

        verify(memberCharacterService).getSelectedCharacter(memberId)
        verify(levelService).getById(1L)
        verify(levelService).getNextLevel(1)

        verify(memberCharacterService).applyExp(
            any(),
            eq(expEarned),
            eq(level1),
            eq(level2)
        )
    }

    // ---------------------------------------------------------
    // 2) 캐릭터가 없으면 예외 던져야 함
    // ---------------------------------------------------------
    @Test
    fun `선택된 캐릭터가 없으면 IllegalStateException`() {
        val memberId = 77L

        whenever(memberCharacterService.getSelectedCharacter(memberId)).thenReturn(null)

        assertThrows(IllegalStateException::class.java) {
            characterUseCase.applyMissionExp(memberId, 100)
        }
    }

    // ---------------------------------------------------------
    // 3) nextLevel 없으면 최고레벨로 처리되고 정상 동작
    // ---------------------------------------------------------
    @Test
    @MockitoSettings(strictness = Strictness.LENIENT)
    fun `nextLevel이 null이면 최고 레벨로 처리되고 정상 동작한다`() {
        val memberId = 55L
        val expEarned = 200

        val mc = MemberCharacter(
            id = 3L,
            memberId = memberId,
            characterId = 5L,
            levelId = 3L,
            totalExp = 0,
            currentExp = 0
        )

        val level3 = Level(id = 3L, levelNumber = 3, requiredExp = 200)

        whenever(memberCharacterService.getSelectedCharacter(memberId)).thenReturn(mc)
        whenever(levelService.getById(3L)).thenReturn(level3)

        // nextLevel = null → 최고레벨
        whenever(levelService.getNextLevel(3)).thenReturn(null)

        // nextLevel이 null 이면 applyExp 의 4번 인자도 null 이어야 함
        doReturn(mc).`when`(memberCharacterService)
            .applyExp(
                any(),
                eq(expEarned),
                eq(level3),
                eq(null)
            )

        val result = characterUseCase.applyMissionExp(memberId, expEarned)

        assertNotNull(result)
        assertEquals(mc.id, result.id)

        verify(memberCharacterService).applyExp(
            any(),
            eq(expEarned),
            eq(level3),
            eq(null)
        )
    }

    @Test
    fun `경험치가 요구치를 초과하면 다음 레벨로 올라간다`() {
        val memberId = 200L
        val expEarned = 150   // 100 필요인데 150 주면 → level up + 50 남음

        val mc = MemberCharacter(
            id = 5L,
            memberId = memberId,
            characterId = 20L,
            levelId = 1L,
            totalExp = 0,
            currentExp = 0
        )

        val level1 = Level(id = 1L, levelNumber = 1, requiredExp = 100)
        val level2 = Level(id = 2L, levelNumber = 2, requiredExp = 200)

        // 현재 캐릭터 조회
        whenever(memberCharacterService.getSelectedCharacter(memberId)).thenReturn(mc)

        // 현재 레벨 & 다음 레벨 조회
        whenever(levelService.getById(1L)).thenReturn(level1)
        whenever(levelService.getNextLevel(1)).thenReturn(level2)

        // applyExp 실행 시 → 레벨업된 mc 반환
        val leveledUpMc = MemberCharacter(
            id = mc.id,
            memberId = mc.memberId,
            characterId = mc.characterId,
            levelId = 2L,
            totalExp = 150,
            currentExp = 50
        )


        doReturn(leveledUpMc).`when`(memberCharacterService)
            .applyExp(
                any(),
                eq(expEarned),
                eq(level1),
                eq(level2)
            )

        // when
        val result = characterUseCase.applyMissionExp(memberId, expEarned)

        // then
        assertNotNull(result)
        assertEquals(2L, result.levelId)
        assertEquals(150, result.totalExp)
        assertEquals(50, result.currentExp)

        verify(memberCharacterService).applyExp(
            any(),
            eq(expEarned),
            eq(level1),
            eq(level2)
        )
    }




}