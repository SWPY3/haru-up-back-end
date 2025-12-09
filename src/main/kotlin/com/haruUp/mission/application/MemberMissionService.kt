package com.haruUp.mission.application

import com.haruUp.mission.domain.MemberMission
import com.haruUp.mission.domain.MemberMissionDto
import com.haruUp.mission.infrastructure.MemberMissionRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class MemberMissionService(
    private val memberMissionRepository: MemberMissionRepository
) {



    // 미션 완료처리
    fun missionCompleted(mission: MemberMission): MemberMission {

        val stored = memberMissionRepository.findByIdOrNull(mission.id!!)
            ?: throw IllegalArgumentException("Mission not found")

        if (stored.isCompleted) {
            return stored // 이미 완료 처리된 경우
        }

        stored.isCompleted = true
        stored.expEarned = mission.expEarned
        stored.updatedAt = LocalDateTime.now()

        return memberMissionRepository.save(stored)
    }


    fun getMission(id : Long) : MemberMission? {
        return memberMissionRepository.findByIdOrNull(id)
    }







}