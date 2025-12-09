package com.haruUp.mission.application

import com.haruUp.character.application.service.CharacterService
import com.haruUp.character.application.service.LevelService
import com.haruUp.character.application.service.MemberCharacterService
import com.haruUp.character.domain.dto.MemberCharacterDto
import com.haruUp.mission.domain.MemberMissionDto
import org.springframework.stereotype.Component

@Component
class MemberMissionUseCase(
    private val memberMissionService: MemberMissionService,
    private val memberCharacterService: MemberCharacterService,
    private val levelService: LevelService
) {

    /**
     * 미션 완료 처리 + 경험치 반영 + 레벨업까지 한번에 처리하는 유즈케이스
     */
    fun missionCompletedWithCharacterLeveling(memberMissionDto: MemberMissionDto): MemberCharacterDto {

        // ----------------------------------------------------------------------
        // 1) 미션 완료 처리
        // ----------------------------------------------------------------------
        val missionCompleted = memberMissionService
            .missionCompleted(
                memberMissionDto.apply { this.isCompleted = true }.toEntity()
            )

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
        // newCurrentExp 가 "현재 레벨의 requiredExp 이상"이면 계속 레벨업
        while (nextLevel != null && newCurrentExp >= currentLevel.requiredExp) {

            // 경험치를 현재 레벨의 필요 경험치만큼 차감
            newCurrentExp -= currentLevel.requiredExp

            // 실제 레벨 증가
            currentLevel = nextLevel

            // 다음 레벨 조회 (LevelService가 자동 생성해줄 수도 있음)
            nextLevel = levelService.getNextLevel(currentLevel.levelNumber)
        }

        // ----------------------------------------------------------------------
        // 6) 최종 계산 결과를 DB에 반영
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