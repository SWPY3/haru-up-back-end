package com.haruUp.mission.application

import com.haruUp.character.application.service.MemberCharacterService
import com.haruUp.character.application.service.LevelService
import com.haruUp.character.domain.Level
import com.haruUp.character.domain.MemberCharacter
import com.haruUp.character.domain.dto.MemberCharacterDto
import com.haruUp.global.error.BusinessException
import com.haruUp.global.error.ErrorCode
import com.haruUp.mission.domain.MemberMissionEntity
import com.haruUp.mission.domain.MemberMissionDto
import com.haruUp.mission.domain.MissionStatus
import com.haruUp.mission.domain.MissionStatusChangeItem
import com.haruUp.mission.domain.MissionStatusChangeRequest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.mockito.junit.jupiter.MockitoExtension
import java.time.LocalDate

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
        val request = MissionStatusChangeRequest(
            missions = listOf(
                MissionStatusChangeItem(memberMissionId = 1L, missionStatus = MissionStatus.COMPLETED)
            )
        )

        val missionEntity = MemberMissionEntity(
            id = 1L,
            memberId = 10L,
            missionId = 99L,
            memberInterestId = 1L,
            missionStatus = MissionStatus.COMPLETED,
            expEarned = 250
        )

        whenever(memberMissionService.updateMission(1L, MissionStatus.COMPLETED)).thenReturn(missionEntity)

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
        val result = useCase.missionChangeStatus(request)

        // -----------------------------------------
        // Then
        // -----------------------------------------
        assertNotNull(result)
        assertEquals(3L, result!!.levelId)
        assertEquals(250, result.totalExp)
        assertEquals(50, result.currentExp)

        verify(memberMissionService).updateMission(1L, MissionStatus.COMPLETED)
        verify(memberCharacterService).getSelectedCharacter(10L)
        verify(levelService).getById(1L)
        verify(levelService, times(3)).getNextLevel(any())
    }

    @Test
    fun `오늘의 미션 조회 단위 테스트`() {

        // given
        val memberId = 10L

        val mission1 = MemberMissionEntity(
            id = 1L,
            memberId = memberId,
            missionId = 101L,
            memberInterestId = 1L,
            expEarned = 10,
            missionStatus = MissionStatus.ACTIVE
        )

        val mission2 = MemberMissionEntity(
            id = 2L,
            memberId = memberId,
            missionId = 102L,
            memberInterestId = 2L,
            expEarned = 20,
            missionStatus = MissionStatus.READY
        )

        whenever(memberMissionService.getTodayMissionsByMemberId(memberId))
            .thenReturn(listOf(mission1, mission2))

        // when
        val result = useCase.missionTodayList(memberId)

        // then
        assertEquals(2, result.size)
        assertEquals(101L, result[0].missionId)
        assertEquals(102L, result[1].missionId)

        verify(memberMissionService).getTodayMissionsByMemberId(memberId)
    }

    @Test
    fun `미션 ACTIVE 상태 처리 단위 테스트`() {

        val request = MissionStatusChangeRequest(
            missions = listOf(
                MissionStatusChangeItem(memberMissionId = 1L, missionStatus = MissionStatus.ACTIVE)
            )
        )

        val missionEntity = MemberMissionEntity(
            id = 1L,
            memberId = 10L,
            missionId = 99L,
            memberInterestId = 1L,
            missionStatus = MissionStatus.ACTIVE,
            expEarned = 0
        )

        whenever(memberMissionService.updateMission(1L, MissionStatus.ACTIVE))
            .thenReturn(missionEntity)

        val result = useCase.missionChangeStatus(request)

        assertNull(result)
        verify(memberMissionService).updateMission(1L, MissionStatus.ACTIVE)
    }

    @Test
    fun `미션 미루기 처리 단위 테스트`() {

        val request = MissionStatusChangeRequest(
            missions = listOf(
                MissionStatusChangeItem(memberMissionId = 1L, missionStatus = MissionStatus.POSTPONED)
            )
        )

        val postponedMission = MemberMissionEntity(
            id = 1L,
            memberId = 10L,
            missionId = 99L,
            memberInterestId = 1L,
            missionStatus = MissionStatus.POSTPONED,
            expEarned = 0,
            targetDate = LocalDate.now().plusDays(1)
        )

        whenever(memberMissionService.handleMissionPostponed(1L))
            .thenReturn(postponedMission)

        val result = useCase.missionChangeStatus(request)

        assertNull(result)
        verify(memberMissionService).handleMissionPostponed(1L)
    }

    @Test
    fun `미션 INACTIVE 상태 처리 단위 테스트`() {

        val request = MissionStatusChangeRequest(
            missions = listOf(
                MissionStatusChangeItem(memberMissionId = 1L, missionStatus = MissionStatus.INACTIVE)
            )
        )

        val missionEntity = MemberMissionEntity(
            id = 1L,
            memberId = 10L,
            missionId = 99L,
            memberInterestId = 1L,
            missionStatus = MissionStatus.INACTIVE,
            expEarned = 0
        )

        whenever(memberMissionService.updateMission(1L, MissionStatus.INACTIVE))
            .thenReturn(missionEntity)

        val result = useCase.missionChangeStatus(request)

        assertNull(result)
        verify(memberMissionService).updateMission(1L, MissionStatus.INACTIVE)
    }
}