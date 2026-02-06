package com.haruUp.mission.application

import com.haruUp.character.application.service.LevelService
import com.haruUp.character.application.service.MemberCharacterService
import com.haruUp.character.domain.dto.MemberCharacterDto
import com.haruUp.mission.domain.MemberMissionEntity
import com.haruUp.mission.domain.MemberMissionDto
import com.haruUp.mission.domain.MissionStatus
import com.haruUp.mission.domain.MissionStatusChangeItem
import com.haruUp.mission.domain.MissionStatusChangeRequest
import com.haruUp.mission.domain.DailyCompletionStatus
import com.haruUp.mission.domain.DailyMissionCountDto
import com.haruUp.mission.domain.MonthlyAttendanceDto
import com.haruUp.mission.domain.MonthlyAttendanceResponseDto
import com.haruUp.mission.domain.MonthlyMissionWithAttendanceDto
import java.time.YearMonth
import com.haruUp.member.application.service.MemberAttendanceService
import com.haruUp.notification.domain.MissionPushTarget
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Component
class MemberMissionUseCase(
    private val memberMissionService: MemberMissionService,
    private val memberCharacterService: MemberCharacterService,
    private val levelService: LevelService,
    private val memberAttendanceService: MemberAttendanceService
) {

    // 미션 조회 (삭제되지 않은 것만, 상태 필터링 가능, 날짜 필터링, 관심사 필터링)
    fun getMemberMissions(
        memberId: Long,
        statuses: List<MissionStatus>? = null,
        targetDate: LocalDate,
        memberInterestId: Long? = null
    ): List<MemberMissionDto> {
        return memberMissionService.getAllMissions(memberId, statuses, targetDate, memberInterestId)
    }

    // 오늘의 미션 조회
    fun missionTodayList(memberId: Long): List<MemberMissionDto> {
        val memberMissions: List<MemberMissionEntity> = memberMissionService.getTodayMissionsByMemberId(memberId)
        return memberMissions.map { it.toDto() }
    }

    /**
     * 미션 상태 벌크 변경 (선택 / 완료 / 미루기 / 실패)
     */
    @Transactional
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
    fun processStatusChange(item: MissionStatusChangeItem): MemberCharacterDto? {

        // missionStatus와 deleted 둘 중 하나는 필수
        require(item.missionStatus != null || item.deleted == true) {
            "missionStatus 또는 deleted 중 하나는 필수입니다. (memberMissionId: ${item.memberMissionId})"
        }

        // deleted가 true인 경우 상태 변경 없이 바로 soft delete
        if (item.deleted == true) {
            memberMissionService.softDeleteMission(item.memberMissionId)
            return null
        }

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

        println("변환전 character level Id : ${mc.levelId}")

        // ----------------------------------------------------------------------
        // 3) 현재 레벨 정보 조회
        var currentLevel = levelService.getById(mc.levelId)

        // 4) 경험치 누적
        var newTotalExp = mc.totalExp + missionCompleted.expEarned
        var newCurrentExp = mc.currentExp + missionCompleted.expEarned

        val maxExp = requireNotNull(currentLevel.maxExp) {
            "Level ${currentLevel.levelNumber} 의 maxExp 가 null 입니다. DB 데이터 확인 필요"
        }

        while (newCurrentExp >= maxExp) {
            newCurrentExp -= maxExp
            currentLevel = levelService.getOrCreateLevel(
                currentLevel.levelNumber + 1
            )
        }

        // 6) 결과 반영
        val updatedMc = memberCharacterService.applyExpWithResolvedValues(
            mc = mc,
            newLevelId = currentLevel.id!!,
            totalExp = newTotalExp,
            currentExp = newCurrentExp
        )

    

        println("변환후 character level Id ${updatedMc.levelId}")

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

    /**
     * 연속 미션 달성 여부 조회
     *
     * @param memberId 사용자 ID
     * @param startDate 시작 날짜
     * @param endDate 종료 날짜
     * @return 날짜별 미션 완료 상태 목록
     */
    fun getCompletionStatusByDateRange(
        memberId: Long,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<DailyCompletionStatus> {
        return memberMissionService.getCompletionStatusByDateRange(memberId, startDate, endDate)
    }

    fun continueMissionMonth(
        memberId: Long,
        targetMonth: String   // "YYYY-MM"
    ): MonthlyMissionWithAttendanceDto {

        val yearMonth = try {
            java.time.YearMonth.parse(targetMonth)
        } catch (e: Exception) {
            throw IllegalArgumentException("잘못된 날짜 형식입니다. YYYY-MM 형식으로 입력해주세요.")
        }

        val targetStartDate: LocalDate = yearMonth.atDay(1)
        val targetEndDate: LocalDate = yearMonth.atEndOfMonth()

        val missionCounts = memberMissionService.findDailyCompletedMissionCount(
            memberId,
            targetStartDate,
            targetEndDate
        )

        val attendanceDates = memberAttendanceService.getAttendanceDates(
            memberId,
            targetStartDate,
            targetEndDate
        ).toSet()

        // 미션 완료일을 Map으로 변환
        val missionCountMap = missionCounts.associateBy { it.targetDate }

        // 미션 완료일 + 출석일 합치기
        val allDates = (missionCountMap.keys + attendanceDates).sorted()

        // 모든 날짜에 대해 DTO 생성
        val missionCountsWithAttendance = allDates.map { date ->
            val mission = missionCountMap[date]
            DailyMissionCountDto(
                targetDate = date,
                completedCount = mission?.completedCount ?: 0,
                isAttendance = attendanceDates.contains(date)
            )
        }

        // 총 미션 완료 수
        val totalMissionCount = missionCounts.sumOf { it.completedCount }

        // 총 출석일 수
        val totalAttendanceCount = attendanceDates.size

        return MonthlyMissionWithAttendanceDto(
            missionCounts = missionCountsWithAttendance,
            totalMissionCount = totalMissionCount,
            totalAttendanceCount = totalAttendanceCount
        )
    }

    /**
     * 월별 출석 횟수 조회
     */
    fun getMonthlyAttendance(
        memberId: Long,
        startTargetMonth: String,
        endTargetMonth: String
    ): MonthlyAttendanceResponseDto {

        val startYearMonth = try {
            YearMonth.parse(startTargetMonth)
        } catch (e: Exception) {
            throw IllegalArgumentException("잘못된 날짜 형식입니다. YYYY-MM 형식으로 입력해주세요.")
        }

        val endYearMonth = try {
            YearMonth.parse(endTargetMonth)
        } catch (e: Exception) {
            throw IllegalArgumentException("잘못된 날짜 형식입니다. YYYY-MM 형식으로 입력해주세요.")
        }

        val startDate = startYearMonth.atDay(1)
        val endDate = endYearMonth.atEndOfMonth()

        val monthlyCountsMap = memberAttendanceService.getMonthlyAttendanceCounts(
            memberId, startDate, endDate
        )

        // 시작월부터 종료월까지 모든 월 생성
        val attendanceDates = mutableListOf<MonthlyAttendanceDto>()
        var current = startYearMonth
        while (!current.isAfter(endYearMonth)) {
            val monthKey = "${current.year}-${current.monthValue.toString().padStart(2, '0')}"
            attendanceDates.add(
                MonthlyAttendanceDto(
                    targetMonth = monthKey,
                    attendanceCount = monthlyCountsMap[monthKey] ?: 0
                )
            )
            current = current.plusMonths(1)
        }

        return MonthlyAttendanceResponseDto(attendanceDates = attendanceDates)
    }

    fun getMembersWithTodayFalseMission() : List<MissionPushTarget> {
        val atStartOfDay = LocalDate.now().atStartOfDay()
        val atEndDate = LocalDate.now().atStartOfDay().plusDays(1)
        return memberMissionService.getMembersWithTodayFalseMission(atStartOfDay, atEndDate)
    }

}