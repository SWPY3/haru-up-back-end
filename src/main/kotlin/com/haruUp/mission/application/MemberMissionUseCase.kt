package com.haruUp.mission.application

import com.haruUp.character.application.service.LevelService
import com.haruUp.character.application.service.MemberCharacterService
import com.haruUp.character.domain.dto.MemberCharacterDto
import com.haruUp.global.error.BusinessException
import com.haruUp.global.error.ErrorCode
import com.haruUp.member.domain.type.MemberStatus
import com.haruUp.mission.domain.MemberMissionEntity
import com.haruUp.mission.domain.MemberMissionDto
import com.haruUp.mission.domain.MissionStatus
import com.haruUp.mission.domain.MissionStatusChangeItem
import com.haruUp.mission.domain.MissionStatusChangeRequest
import org.springframework.stereotype.Component

@Component
class MemberMissionUseCase(
    private val memberMissionService: MemberMissionService,
    private val memberCharacterService: MemberCharacterService,
    private val levelService: LevelService
) {

    // 미션 조회 (삭제되지 않은 것만, 상태 필터링 가능)
    fun getMemberMissions(memberId: Long, statuses: List<MissionStatus>? = null): List<MemberMissionDto> {
        return memberMissionService.getAllMissions(memberId, statuses)
    }

    // 오늘의 미션 조회
    fun missionTodayList(memberId: Long): List<MemberMissionDto> {
        val memberMissions: List<MemberMissionEntity> = memberMissionService.getTodayMissionsByMemberId(memberId)
        return memberMissions.map { it.toDto() }
    }

    /**
     * 미션 상태 벌크 변경 (선택 / 완료 / 미루기 / 실패)
     */
    fun missionChangeStatus(request: MissionStatusChangeRequest): MemberCharacterDto? {
        var lastCharacterDto: MemberCharacterDto? = null

        for (item in request.missions) {
            val result = processStatusChange(item)
            if (result != null) {
                lastCharacterDto = result
            }
        }

        return lastCharacterDto
    }

    /**
     * 개별 미션 상태 변경 처리
     */
    private fun processStatusChange(item: MissionStatusChangeItem): MemberCharacterDto? {
        // COMPLETED 상태인 경우 경험치 처리 필요
        if (item.missionStatus == MissionStatus.COMPLETED) {
            return handleMissionCompleted(item.memberMissionId)
        }

        if (item.missionStatus == MissionStatus.POSTPONED) {
            memberMissionService.handleMissionPostponed(item.memberMissionId)
            return null
        }

        // 그 외의 경우 (status 변경)
        memberMissionService.updateMission(item.memberMissionId, item.missionStatus)
        return null
    }


    /**
     * 미션 완료 → 경험치 반영 → 레벨업 처리
     */
    private fun handleMissionCompleted(memberMissionId: Long): MemberCharacterDto {

        // ----------------------------------------------------------------------
        // 1) 미션 완료 처리
        // ----------------------------------------------------------------------
        val missionCompleted = memberMissionService.updateMission(memberMissionId, MissionStatus.COMPLETED)

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

    /**
     * 특정 관심사에 해당하는 미션 리셋 (soft delete)
     *
     * @param memberId 사용자 ID
     * @param memberInterestId 멤버 관심사 ID
     * @return 삭제된 미션 개수
     */
    fun resetMissionsByMemberInterestId(memberId: Long, memberInterestId: Long): Int {
        return memberMissionService.deleteMissionsByMemberInterestId(memberId, memberInterestId)
    }
}