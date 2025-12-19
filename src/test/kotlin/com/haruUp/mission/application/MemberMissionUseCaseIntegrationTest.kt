package com.haruUp.mission.application

import com.haruUp.character.domain.Level
import com.haruUp.character.domain.MemberCharacter
import com.haruUp.character.infrastructure.LevelRepository
import com.haruUp.character.infrastructure.MemberCharacterRepository
import com.haruUp.mission.domain.MemberMission
import com.haruUp.mission.domain.MissionStatus
import com.haruUp.mission.infrastructure.MemberMissionRepository
import jakarta.transaction.Transactional
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
@Transactional
class MemberMissionUseCaseIntegrationTest @Autowired constructor(
    private val useCase: MemberMissionUseCase,
    private val levelRepo: LevelRepository,
    private val memberCharacterRepo: MemberCharacterRepository,
    private val missionRepo: MemberMissionRepository
) {

    @BeforeEach
    fun setup() {
        missionRepo.deleteAll()
        memberCharacterRepo.deleteAll()
        levelRepo.deleteAll()

        // Level 생성 (1 → 3)
        levelRepo.save(Level(levelNumber = 1, requiredExp = 100))
        levelRepo.save(Level(levelNumber = 2, requiredExp = 100))
        levelRepo.save(Level(levelNumber = 3, requiredExp = 100))

        // 캐릭터 생성
        memberCharacterRepo.save(
            MemberCharacter(
                memberId = 1L,
                characterId = 1L,
                levelId = 1L,
                totalExp = 0,
                currentExp = 0
            )
        )
    }

    @Test
    fun `미션 완료 후 DB에서 실제로 레벨업이 일어난다`() {

        // Given
        val mission = missionRepo.save(
            MemberMission(
                memberId = 10L,
                missionId = 99L,
                memberInterestId = 1L,
                isCompleted = false,
                expEarned = 250,
                missionStatus = MissionStatus.COMPLETED
            )
        )

        val dto = mission.toDto()

        // When
        val result = useCase.missionChangeStatus(dto)

        // Then
        assertEquals(3, result.levelId)     // 250 exp → 2단계 레벨업
        assertEquals(250, result.totalExp)
        assertEquals(50, result.currentExp)

        val mc = memberCharacterRepo.findByMemberId(10L)
            ?: fail("캐릭터가 DB에 존재해야 합니다.")

        assertEquals(3L, mc.levelId)
        assertEquals(250, mc.totalExp)
        assertEquals(50, mc.currentExp)
    }

    @Test
    fun `미션 완료 후 1단계 레벨업만 발생한다`() {

        // Given
        val mission = missionRepo.save(
            MemberMission(
                memberId = 10L,
                missionId = 100L,
                memberInterestId = 1L,
                isCompleted = false,
                expEarned = 120,   // 100 → 레벨업, 20 잔여
                missionStatus = MissionStatus.COMPLETED
            )
        )

        val dto = mission.toDto()

        // When
        val result = useCase.missionChangeStatus(dto)

        // Then
        assertEquals(2L, result.levelId)
        assertEquals(120, result.totalExp)
        assertEquals(20, result.currentExp)

        val mc = memberCharacterRepo.findByMemberId(10L)!!
        assertEquals(2L, mc.levelId)
        assertEquals(20, mc.currentExp)
    }
}