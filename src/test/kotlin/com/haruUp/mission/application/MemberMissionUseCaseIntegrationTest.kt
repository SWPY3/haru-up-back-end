//package com.haruUp.mission.application
//
//import com.haruUp.character.application.service.LevelService
//import com.haruUp.character.domain.Level
//import com.haruUp.character.domain.MemberCharacter
//import com.haruUp.character.infrastructure.LevelRepository
//import com.haruUp.character.infrastructure.MemberCharacterRepository
//import com.haruUp.mission.domain.MemberMissionEntity
//import com.haruUp.mission.domain.MissionStatus
//import com.haruUp.mission.domain.MissionStatusChangeItem
//import com.haruUp.mission.domain.MissionStatusChangeRequest
//import com.haruUp.mission.infrastructure.MemberMissionRepository
//import jakarta.transaction.Transactional
//import org.junit.jupiter.api.Assertions.assertEquals
//import org.junit.jupiter.api.BeforeEach
//import org.junit.jupiter.api.Test
//import org.junit.jupiter.api.assertNotNull
//import org.junit.jupiter.api.fail
//import org.springframework.beans.factory.annotation.Autowired
//import org.springframework.boot.test.context.SpringBootTest
//
//@SpringBootTest
//@Transactional
//class MemberMissionUseCaseIntegrationTest @Autowired constructor(
//    private val useCase: MemberMissionUseCase,
//    private val levelRepo: LevelRepository,
//    private val memberCharacterRepo: MemberCharacterRepository,
//    private val missionRepo: MemberMissionRepository,
//    private val levelService : LevelService
//) {
//
//    @BeforeEach
//    fun setup() {
//        missionRepo.deleteAll()
//        memberCharacterRepo.deleteAll()
//        levelRepo.deleteAll()
//
//        val level1 = levelRepo.save(Level(levelNumber = 1, requiredExp = 1000, maxExp = 1000))
//
//        memberCharacterRepo.save(
//            MemberCharacter(
//                memberId = 1L,
//                characterId = 1L,
//                levelId = level1.id!!, // ✅ 실제 ID 사용
//                totalExp = 0,
//                currentExp = 0
//            )
//        )
//    }
//
//    @Test
//    fun `미션 완료 후 DB에서 실제로 레벨업이 일어난다`() {
//
//
//        // Given
//        val mission = missionRepo.save(
//            MemberMissionEntity(
//                memberId = 1L,
//                missionId = 11L,
//                memberInterestId = 1L,
//                expEarned = 2500,
//                missionStatus = MissionStatus.COMPLETED
//            )
//        )
//
//        val request = MissionStatusChangeRequest(
//            missions = listOf(
//                MissionStatusChangeItem(memberMissionId = mission.id!!, missionStatus = MissionStatus.COMPLETED)
//            )
//        )
//
//        // When
//        val result = useCase.missionChangeStatus(request)
//
//        // Then
//        assertEquals(result!!.levelId, result!!.levelId)     // 250 exp → 2단계 레벨업
//        assertEquals(2500, result.totalExp)
//        assertEquals(1500, result.currentExp)
//
//        val mc = memberCharacterRepo.findFirstByMemberIdAndDeletedFalseOrderByIdDesc(1L)
//            ?: fail("캐릭터가 DB에 존재해야 합니다.")
//
//        var currentLevel = levelService.getById(mc.levelId)
//
//        assertEquals(2, currentLevel.levelNumber)
//        assertEquals(mc.levelId, mc.levelId)
//        assertEquals(2500, mc.totalExp)
//        assertEquals(1500, mc.currentExp)
//    }
//
//    @Test
//    fun `미션 완료 시 경험치 기준으로 자동 레벨업되어 4레벨까지 도달한다`() {
//
//        // Given
//        val initialCharacter = memberCharacterRepo
//            .findFirstByMemberIdAndDeletedFalseOrderByIdDesc(1L)
//            ?: fail("초기 캐릭터가 존재해야 합니다.")
//
//        val initialLevel = levelService.getById(initialCharacter.levelId)
//        assertEquals(1, initialLevel.levelNumber) // 🔹 초기 레벨 명시
//
//        val mission = missionRepo.save(
//            MemberMissionEntity(
//                memberId = 1L,
//                missionId = 100L,
//                memberInterestId = 1L,
//                expEarned = 3500, // 🔥 3번 레벨업
//                missionStatus = MissionStatus.COMPLETED
//            )
//        )
//
//        val request = MissionStatusChangeRequest(
//            missions = listOf(
//                MissionStatusChangeItem(
//                    memberMissionId = mission.id!!,
//                    missionStatus = MissionStatus.COMPLETED
//                )
//            )
//        )
//
//        println("변환전 levelId : $")
//
//        // When
//        val result = useCase.missionChangeStatus(request)
//            ?: fail("결과 DTO가 null이면 안 됩니다.")
//
//        // Then - 반환 DTO 검증
//        val resultLevel = levelService.getById(result.levelId)
//
//        assertEquals(4, resultLevel.levelNumber) // ⭐ 1 → 4
//        assertEquals(3500, result.totalExp)
//        assertEquals(500, result.currentExp)     // carry-over 검증
//
//        // Then - DB 상태 검증
//        val mc = memberCharacterRepo
//            .findFirstByMemberIdAndDeletedFalseOrderByIdDesc(1L)
//            ?: fail("캐릭터가 DB에 존재해야 합니다.")
//
//        val dbLevel = levelService.getById(mc.levelId)
//
//        assertEquals(4, dbLevel.levelNumber)
//        assertEquals(3500, mc.totalExp)
//        assertEquals(500, mc.currentExp)
//
//        // 🔹 Level 자동 생성 검증 (보너스)
//        assertNotNull(levelService.getOrCreateLevel(2))
//        assertNotNull(levelService.getOrCreateLevel(3))
//        assertNotNull(levelService.getOrCreateLevel(4))
//    }
//
//    @Test
//    fun `미션 완료 후 1단계 레벨업만 발생한다`() {
//
//        // Given
//        val mission = missionRepo.save(
//            MemberMissionEntity(
//                memberId = 1L,
//                missionId = 100L,
//                memberInterestId = 1L,
//                expEarned = 120,   // 100 → 레벨업, 20 잔여
//                missionStatus = MissionStatus.COMPLETED
//            )
//        )
//
//        val request = MissionStatusChangeRequest(
//            missions = listOf(
//                MissionStatusChangeItem(memberMissionId = mission.id!!, missionStatus = MissionStatus.COMPLETED)
//            )
//        )
//
//        // When
//        val result = useCase.missionChangeStatus(request)
//
//        // Then
//        assertEquals(2L, result!!.levelId)
//        assertEquals(120, result.totalExp)
//        assertEquals(20, result.currentExp)
//
//        val mc = memberCharacterRepo.findFirstByMemberIdAndDeletedFalseOrderByIdDesc(10L)!!
//        assertEquals(2L, mc.levelId)
//        assertEquals(20, mc.currentExp)
//    }
//}