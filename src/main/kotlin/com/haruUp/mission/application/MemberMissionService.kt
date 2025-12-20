package com.haruUp.mission.application

import com.haruUp.member.domain.type.MemberStatus
import com.haruUp.mission.domain.MemberMission
import com.haruUp.mission.domain.MemberMissionDto
import com.haruUp.mission.domain.MissionStatus
import com.haruUp.mission.infrastructure.MemberMissionRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.LocalDateTime

@Service
class MemberMissionService(
    private val memberMissionRepository: MemberMissionRepository
) {

    // 전체 미션 조회
    fun getAllMissions(memberId: Long): List<MemberMissionDto> {
        return memberMissionRepository.findByMemberId(memberId)
            .map { mission -> mission.toDto() }
            .toList()
    }

    /**
     * 미션 상태 및 postponedAt 변경
     * - status: 변경할 상태 (null이면 변경 안함)
     * - postponedAt: 미루기 날짜 (null이면 변경 안함)
     */
    fun updateMission(missionId: Long, status: MissionStatus?, postponedAt: LocalDate?): MemberMission {
        val stored = memberMissionRepository.findByIdOrNull(missionId)
            ?: throw IllegalArgumentException("미션을 찾을 수 없습니다.")

        // 상태 변경
        if (status != null) {
            // 이미 완료된 미션은 상태 변경 불가
            if (stored.missionStatus == MissionStatus.COMPLETED && status != MissionStatus.COMPLETED) {
                throw IllegalStateException("이미 완료된 미션은 상태를 변경할 수 없습니다.")
            }
            stored.missionStatus = status
        }

        // postponedAt 변경
        if (postponedAt != null) {
            stored.postponedAt = postponedAt
        }

        stored.updatedAt = LocalDateTime.now()

        return memberMissionRepository.save(stored)
    }


    fun getMission(id : Long) : MemberMission? {
        return memberMissionRepository.findByIdOrNull(id)
    }

    fun getTodayMissionsByMemberId(memberId: Long): List<MemberMission> {
       return memberMissionRepository.getTodayMissionsByMemberId(memberId)
    }




}