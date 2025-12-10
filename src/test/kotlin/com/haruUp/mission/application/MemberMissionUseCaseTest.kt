package com.haruUp.mission.application

import com.haruUp.character.application.service.MemberCharacterService
import com.haruUp.character.application.service.LevelService
import com.haruUp.character.domain.Level
import com.haruUp.character.domain.MemberCharacter
import com.haruUp.character.domain.dto.MemberCharacterDto
import com.haruUp.mission.domain.MemberMissionDto
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.mockito.junit.jupiter.MockitoExtension

@ExtendWith(MockitoExtension::class)
class MemberMissionUseCaseUnitTest {

    @Mock lateinit var memberMissionService: MemberMissionService
    @Mock lateinit var memberCharacterService: MemberCharacterService
    @Mock lateinit var levelService: LevelService

    @InjectMocks lateinit var useCase: MemberMissionUseCase

    private fun mcDto(mc: MemberCharacter) = MemberCharacterDto(
        id = mc.id!!,
        memberId = mc.memberId,
        characterId = mc.characterId,
        levelId = mc.levelId,
        totalExp = mc.totalExp,
        currentExp = mc.currentExp
    )

    @Test
    fun `미션 완료 + 경험치 반영 + 레벨업 단위 테스트`() {

        // -----------------------------------------
        // Given
        // -----------------------------------------
        val missionDto = MemberMissionDto(
            id = 1L,
            memberId = 10L,
            missionId = 99L,
            isCompleted = false,
            expEarned = 250     // 250 경험치 획득
        )

        val missionEntity = missionDto.toEntity().apply { isCompleted = true }

        whenever(memberMissionService.missionCompleted(any())).thenReturn(missionEntity)

        // 캐릭터 현재 상태
        val mc = MemberCharacter(
            id = 5L,
            memberId = 10L,
            characterId = 1L,
            levelId = 1L,
            totalExp = 0,
            currentExp = 0
        )
        whenever(memberCharacterService.getSelectedCharacter(10L)).thenReturn(mc)

        // 레벨 정보
        val level1 = Level(id = 1L, levelNumber = 1, requiredExp = 100)
        val level2 = Level(id = 2L, levelNumber = 2, requiredExp = 100)
        val level3 = Level(id = 3L, levelNumber = 3, requiredExp = 100)

        whenever(levelService.getById(1L)).thenReturn(level1)
        whenever(levelService.getNextLevel(1)).thenReturn(level2)
        whenever(levelService.getNextLevel(2)).thenReturn(level3)
        whenever(levelService.getNextLevel(3)).thenReturn(null)

        // applyExpWithResolvedValues 결과는 레벨업된 캐릭터라고 가정
        val updatedMc = MemberCharacter(
            id = 5L,
            memberId = 10L,
            characterId = 1L,
            levelId = 3L,    // 250 exp → 1레벨업(150) → 2레벨업(50)
            totalExp = 250,
            currentExp = 50
        )

        whenever(
            memberCharacterService.applyExpWithResolvedValues(
                any(), any(), any(), any()
            )
        ).thenReturn(updatedMc)

        // -----------------------------------------
        // When
        // -----------------------------------------
        val result = useCase.missionChangeStatus(missionDto)

        // -----------------------------------------
        // Then
        // -----------------------------------------
        assertEquals(3L, result.levelId)
        assertEquals(250, result.totalExp)
        assertEquals(50, result.currentExp)

        verify(memberMissionService).missionCompleted(any())
        verify(memberCharacterService).getSelectedCharacter(10L)
        verify(levelService).getById(1L)
        verify(levelService, times(3)).getNextLevel(any())
    }
}