package com.haruUp.mission.application

import com.haruUp.mission.domain.CustomMissionCreateRequest
import com.haruUp.mission.domain.CustomMissionType
import com.haruUp.mission.domain.MemberCustomMissionDto
import com.haruUp.mission.domain.MemberCustomMissionEntity
import com.haruUp.mission.domain.MissionStatus
import com.haruUp.mission.infrastructure.MemberCustomMissionRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime

@Service
class MemberCustomMissionService(
    private val memberCustomMissionRepository: MemberCustomMissionRepository
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun createMission(memberId: Long, request: CustomMissionCreateRequest): MemberCustomMissionDto {
        val entity = MemberCustomMissionEntity(
            memberId = memberId,
            missionContent = request.missionContent,
            type = request.type,
            missionStatus = MissionStatus.ACTIVE,
            targetDate = request.targetDate ?: LocalDate.now()
        )

        val saved = memberCustomMissionRepository.save(entity)
        logger.info("커스텀 미션 생성 완료 - memberId: $memberId, customMissionId: ${saved.id}, type: ${request.type}")
        return saved.toDto()
    }

    fun getMissions(
        memberId: Long,
        targetDate: LocalDate?,
        statuses: List<MissionStatus>?,
        type: CustomMissionType?
    ): List<MemberCustomMissionDto> {
        val missions = when {
            type != null && targetDate != null ->
                memberCustomMissionRepository.findByMemberIdAndTypeAndTargetDateAndDeletedFalse(memberId, type, targetDate)
            type != null ->
                memberCustomMissionRepository.findByMemberIdAndTypeAndDeletedFalse(memberId, type)
            targetDate != null ->
                memberCustomMissionRepository.findByMemberIdAndTargetDateAndDeletedFalse(memberId, targetDate)
            else ->
                memberCustomMissionRepository.findByMemberIdAndDeletedFalse(memberId)
        }

        val filtered = if (statuses.isNullOrEmpty()) {
            missions
        } else {
            missions.filter { it.missionStatus in statuses }
        }

        return filtered.map { it.toDto() }
    }

    @Transactional
    fun updateStatus(memberId: Long, missionId: Long, status: MissionStatus): MemberCustomMissionDto {
        val mission = memberCustomMissionRepository.findByIdAndDeletedFalse(missionId)
            ?: throw IllegalArgumentException("커스텀 미션을 찾을 수 없습니다: customMissionId=$missionId")

        if (mission.memberId != memberId) {
            throw IllegalArgumentException("본인의 미션만 변경할 수 있습니다.")
        }

        if (mission.missionStatus == MissionStatus.COMPLETED && status != MissionStatus.COMPLETED) {
            throw IllegalStateException("이미 완료된 미션은 상태를 변경할 수 없습니다.")
        }

        mission.missionStatus = status
        mission.updatedAt = LocalDateTime.now()

        val saved = memberCustomMissionRepository.save(mission)
        logger.info("커스텀 미션 상태 변경 - customMissionId: $missionId, status: $status")
        return saved.toDto()
    }

    @Transactional
    fun updateContent(memberId: Long, missionId: Long, missionContent: String): MemberCustomMissionDto {
        val mission = memberCustomMissionRepository.findByIdAndDeletedFalse(missionId)
            ?: throw IllegalArgumentException("커스텀 미션을 찾을 수 없습니다: customMissionId=$missionId")

        if (mission.memberId != memberId) {
            throw IllegalArgumentException("본인의 미션만 수정할 수 있습니다.")
        }

        if (mission.missionStatus == MissionStatus.COMPLETED) {
            throw IllegalStateException("완료된 미션은 수정할 수 없습니다.")
        }

        mission.missionContent = missionContent
        mission.updatedAt = LocalDateTime.now()

        val saved = memberCustomMissionRepository.save(mission)
        logger.info("커스텀 미션 내용 수정 - customMissionId: $missionId")
        return saved.toDto()
    }

    @Transactional
    fun softDeleteMission(memberId: Long, missionId: Long) {
        val mission = memberCustomMissionRepository.findByIdAndDeletedFalse(missionId)
            ?: throw IllegalArgumentException("커스텀 미션을 찾을 수 없습니다: customMissionId=$missionId")

        if (mission.memberId != memberId) {
            throw IllegalArgumentException("본인의 미션만 삭제할 수 있습니다.")
        }

        mission.softDelete()
        memberCustomMissionRepository.save(mission)
        logger.info("커스텀 미션 삭제 완료 - customMissionId: $missionId")
    }

    @Transactional
    fun deleteAllByMemberId(memberId: Long) {
        memberCustomMissionRepository.softDeleteAllByMemberId(memberId)
        logger.info("커스텀 미션 일괄 삭제 완료 - memberId: $memberId")
    }
}
