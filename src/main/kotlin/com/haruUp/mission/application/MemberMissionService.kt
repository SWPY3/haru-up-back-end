package com.haruUp.mission.application

import org.slf4j.LoggerFactory
import com.haruUp.interest.repository.MemberInterestJpaRepository
import com.haruUp.interest.repository.InterestEmbeddingJpaRepository
import com.haruUp.mission.domain.DailyCompletionStatus
import com.haruUp.mission.domain.MemberMissionEntity
import com.haruUp.mission.domain.MemberMissionDto
import com.haruUp.mission.domain.MissionStatus
import com.haruUp.mission.infrastructure.MemberMissionRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime

@Service
class MemberMissionService(
    private val memberMissionRepository: MemberMissionRepository,
    private val memberInterestRepository: MemberInterestJpaRepository,
    private val interestEmbeddingRepository: InterestEmbeddingJpaRepository
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 미션 조회 (삭제되지 않은 것만, 상태 필터링 가능, 날짜 필터링, 관심사 필터링)
     * - member_mission에서 mission_content, difficulty 직접 조회
     * - member_interest에서 direct_full_path 조회
     * - interest_embeddings에서 full_path 조회
     *
     * @param memberId 멤버 ID
     * @param statuses 조회할 미션 상태 목록 (null이면 전체 조회)
     * @param targetDate 조회할 날짜
     * @param memberInterestId 멤버 관심사 ID (null이면 전체 조회)
     */
    fun getAllMissions(memberId: Long, statuses: List<MissionStatus>? = null, targetDate: LocalDate, memberInterestId: Long? = null): List<MemberMissionDto> {
        val allMissions = memberMissionRepository.findByMemberIdAndDeletedFalse(memberId)

        // 날짜 필터링
        val dateFiltered = allMissions.filter { it.targetDate == targetDate }

        // 관심사 필터링
        val interestFiltered = if (memberInterestId != null) {
            dateFiltered.filter { it.memberInterestId == memberInterestId }
        } else {
            dateFiltered
        }

        // 상태 필터링
        val missions = if (statuses.isNullOrEmpty()) {
            interestFiltered
        } else {
            interestFiltered.filter { it.missionStatus in statuses }
        }

        return missions.map { mission ->
            // member_interest에서 direct_full_path 조회
            val memberInterest = memberInterestRepository.findByIdOrNull(mission.memberInterestId)

            // interest_embeddings에서 full_path 조회
            val interestEmbedding = memberInterest?.let {
                interestEmbeddingRepository.findByIdOrNull(it.interestId)
            }

            // entity에서 직접 데이터 조회
            mission.toDto(
                fullPath = interestEmbedding?.fullPath,
                directFullPath = memberInterest?.directFullPath
            )
        }
    }

    /**
     * 미션 미루기 처리
     * - 기존 row는 그대로 유지
     * - 새로운 row 생성: missionStatus=POSTPONED, targetDate=내일
     */
    fun handleMissionPostponed(memberMissionId: Long): MemberMissionEntity {
        val stored = memberMissionRepository.findByIdOrNull(memberMissionId)
            ?: throw IllegalArgumentException("미션을 찾을 수 없습니다.")

        // 새로운 row 생성 (기존 row는 그대로 유지, 미션 내용과 난이도 복사)
        val postponedMission = MemberMissionEntity(
            memberId = stored.memberId,
            memberInterestId = stored.memberInterestId,
            missionContent = stored.missionContent,
            difficulty = stored.difficulty,
            labelName = stored.labelName,
            embedding = stored.embedding,
            missionStatus = MissionStatus.POSTPONED,
            expEarned = stored.expEarned,
            targetDate = LocalDate.now().plusDays(1)
        )

        return memberMissionRepository.save(postponedMission)
    }

    /**
     * 미션 상태 변경
     * - status: 변경할 상태 (null이면 변경 안함)
     */
    fun updateMission(memberMissionId: Long, status: MissionStatus?): MemberMissionEntity {
        val stored = memberMissionRepository.findByIdOrNull(memberMissionId)
            ?: throw IllegalArgumentException("미션을 찾을 수 없습니다.")

        // 상태 변경
        if (status != null) {
            // 이미 완료된 미션은 상태 변경 불가
            if (stored.missionStatus == MissionStatus.COMPLETED && status != MissionStatus.COMPLETED) {
                throw IllegalStateException("이미 완료된 미션은 상태를 변경할 수 없습니다.")
            }
            stored.missionStatus = status
        }

        stored.updatedAt = LocalDateTime.now()

        return memberMissionRepository.save(stored)
    }

    fun getTodayMissionsByMemberId(memberId: Long): List<MemberMissionEntity> {
       return memberMissionRepository.getTodayMissionsByMemberId(memberId)
    }

    /**
     * 사용자가 선택한 미션들을 ACTIVE 상태로 변경
     *
     * @param memberId 사용자 ID
     * @param memberMissionIds 선택한 member_mission ID 목록
     * @return 업데이트된 member_mission ID 목록
     */
    @Transactional
    fun saveMissions(memberId: Long, memberMissionIds: List<Long>): List<Long> {
        val updatedMemberMissionIds = mutableListOf<Long>()
        val today = LocalDate.now()

        memberMissionIds.forEach { memberMissionId ->
            // memberMissionId로 조회
            val memberMission = memberMissionRepository.findByIdOrNull(memberMissionId)
                ?: throw IllegalArgumentException("등록된 미션을 찾을 수 없습니다: memberMissionId=$memberMissionId")

            // 본인의 미션인지 확인
            if (memberMission.memberId != memberId) {
                throw IllegalArgumentException("본인의 미션만 선택할 수 있습니다: memberMissionId=$memberMissionId")
            }

            // missionStatus를 ACTIVE로, targetDate를 오늘로, isSelected를 true로 변경
            memberMission.missionStatus = MissionStatus.ACTIVE
            memberMission.targetDate = today
            memberMission.isSelected = true
            memberMission.updatedAt = LocalDateTime.now()

            val saved = memberMissionRepository.save(memberMission)
            saved.id?.let { updatedMemberMissionIds.add(it) }
            logger.info("미션 활성화 완료: memberId=$memberId, memberMissionId=$memberMissionId")
        }

        logger.info("미션 선택 완료 - 업데이트된 개수: ${updatedMemberMissionIds.size}")

        return updatedMemberMissionIds
    }

    fun deleteMemberMissionsByMemberId(memberId: Long) {
        memberMissionRepository.softDeleteAllByMemberId(memberId)
        logger.info("멤버 미션 삭제 완료 - memberId: $memberId")
    }

    /**
     * 특정 관심사에 해당하는 미션 soft delete
     *
     * @param memberId 사용자 ID
     * @param memberInterestId 멤버 관심사 ID
     * @return 삭제된 미션 개수
     */
    @Transactional
    fun deleteMissionsByMemberInterestId(memberId: Long, memberInterestId: Long): Int {
        val deletedCount = memberMissionRepository.softDeleteByMemberIdAndInterestId(
            memberId = memberId,
            memberInterestId = memberInterestId,
            deletedAt = LocalDateTime.now()
        )
        logger.info("멤버 미션 삭제 완료 - memberId: $memberId, memberInterestId: $memberInterestId, deletedCount: $deletedCount")
        return deletedCount
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
        // COMPLETED 상태인 미션들의 targetDate 조회
        val completedDates = memberMissionRepository.findCompletedDatesByMemberIdAndDateRange(
            memberId = memberId,
            startDate = startDate,
            endDate = endDate
        ).toSet()

        // startDate부터 endDate까지 각 날짜별로 완료 여부 생성
        val result = mutableListOf<DailyCompletionStatus>()
        var currentDate = startDate
        while (!currentDate.isAfter(endDate)) {
            result.add(
                DailyCompletionStatus(
                    targetDate = currentDate,
                    isCompleted = currentDate in completedDates
                )
            )
            currentDate = currentDate.plusDays(1)
        }
        return result
    }
}