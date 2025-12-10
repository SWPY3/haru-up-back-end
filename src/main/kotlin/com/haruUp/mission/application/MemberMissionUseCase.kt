package com.haruUp.mission.application

import com.haruUp.character.application.service.LevelService
import com.haruUp.character.application.service.MemberCharacterService
import com.haruUp.character.domain.dto.MemberCharacterDto
import com.haruUp.global.error.BusinessException
import com.haruUp.global.error.ErrorCode
import com.haruUp.member.domain.type.MemberStatus
import com.haruUp.mission.domain.MemberMission
import com.haruUp.mission.domain.MemberMissionDto
import com.haruUp.mission.domain.MissionStatus
import org.springframework.stereotype.Component

@Component
class MemberMissionUseCase(
    private val memberMissionService: MemberMissionService,
    private val memberCharacterService: MemberCharacterService,
    private val levelService: LevelService
) {

    /**
     * 미션 상태 변경(완료 / 미루기 / 실패)
     */
    fun missionChangeStatus(memberMissionDto: MemberMissionDto): MemberCharacterDto {

        return when (memberMissionDto.missionStatus) {

            // 완료
            MissionStatus.COMPLETED -> handleMissionCompleted(memberMissionDto)

            // 선택
            MissionStatus.ACTIVE -> {
                memberMissionService.activeMission(memberMissionDto.toEntity()).toDto()
                throw BusinessException(ErrorCode.NOT_FOUND, "미션을 찾을수 없습니다.")
            }

            // 미루기
            MissionStatus.POSTPONED -> {
                memberMissionService.postponeMission(memberMissionDto.toEntity()).toDto()
                throw BusinessException(ErrorCode.NOT_FOUND, "미션을 찾을수 없습니다.")
            }

            // 삭제
            MissionStatus.INACTIVE -> {
                memberMissionService.failMission(memberMissionDto.toEntity()).toDto()
                throw BusinessException(ErrorCode.NOT_FOUND, "미션을 찾을수 없습니다.")
            }

            else -> throw IllegalArgumentException("Invalid mission status: ${memberMissionDto.missionStatus}")
        }
    }


    /**
     * 미션 완료 → 경험치 반영 → 레벨업 처리
     */
    private fun handleMissionCompleted(dto: MemberMissionDto): MemberCharacterDto {

        // ----------------------------------------------------------------------
        // 1) 미션 완료 처리
        // ----------------------------------------------------------------------
        val missionCompleted = memberMissionService
            .missionCompleted(dto.apply { this.isCompleted = true }.toEntity())

        // ----------------------------------------------------------------------
        // 2) 선택된 캐릭터 조회
        // ----------------------------------------------------------------------
        val mc = memberCharacterService.getSelectedCharacter(missionCompleted.memberId)
            ?: throw IllegalStateException("선택된 캐릭터가 없습니다.")

        // ----------------------------------------------------------------------
        // 3) 현재 레벨 정보 조회
        // ----------------------------------------------------------------------
        var currentLevel = levelService.getById(mc.levelId)
        var nextLevel = levelService.getNextLevel(currentLevel.levelNumber)

        // ----------------------------------------------------------------------
        // 4) 경험치 누적 계산
        // ----------------------------------------------------------------------
        var newTotalExp = mc.totalExp + missionCompleted.expEarned
        var newCurrentExp = mc.currentExp + missionCompleted.expEarned

        // ----------------------------------------------------------------------
        // ⭐ 5) 레벨업 반복 처리
        // ----------------------------------------------------------------------
        while (nextLevel != null && newCurrentExp >= currentLevel.requiredExp) {

            newCurrentExp -= currentLevel.requiredExp
            currentLevel = nextLevel

            // 다음 레벨 조회
            nextLevel = levelService.getNextLevel(currentLevel.levelNumber)
        }

        // ----------------------------------------------------------------------
        // 6) 최종 계산 결과를 DB 반영
        // ----------------------------------------------------------------------
        val updatedMc = memberCharacterService.applyExpWithResolvedValues(
            mc = mc,
            newLevelId = currentLevel.id!!,
            totalExp = newTotalExp,
            currentExp = newCurrentExp
        )

        return updatedMc.toDto()
    }
}