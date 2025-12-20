package com.haruUp.mission.application

import org.slf4j.LoggerFactory
import com.haruUp.mission.domain.MemberMission
import com.haruUp.mission.domain.MemberMissionDto
import com.haruUp.mission.domain.MemberMissionSelectionRequest
import com.haruUp.mission.domain.MissionStatus
import com.haruUp.mission.infrastructure.MemberMissionRepository
import com.haruUp.missionembedding.repository.MissionEmbeddingRepository
import com.haruUp.missionembedding.service.MissionEmbeddingService
import kotlinx.coroutines.runBlocking
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime

@Service
class MemberMissionService(
    private val memberMissionRepository: MemberMissionRepository,
    private val missionEmbeddingRepository: MissionEmbeddingRepository,
    private val missionEmbeddingService: MissionEmbeddingService
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    // 전체 미션 조회
    fun getAllMissions(memberId: Long): List<MemberMissionDto> {
        return memberMissionRepository.findByMemberId(memberId)
            .map { mission -> mission.toDto() }
            .toList()
    }

    /**
     * 미션 미루기 처리
     * - 기존 row는 그대로 유지
     * - 새로운 row 생성: missionStatus=POSTPONED, targetDate=내일
     */
    fun handleMissionPostponed(missionId: Long): MemberMission {
        val stored = memberMissionRepository.findByIdOrNull(missionId)
            ?: throw IllegalArgumentException("미션을 찾을 수 없습니다.")

        // 새로운 row 생성 (기존 row는 그대로 유지)
        val postponedMission = MemberMission(
            memberId = stored.memberId,
            missionId = stored.missionId,
            memberInterestId = stored.memberInterestId,
            missionStatus = MissionStatus.POSTPONED,
            expEarned = 0,
            targetDate = LocalDate.now().plusDays(1)
        )

        return memberMissionRepository.save(postponedMission)
    }

    /**
     * 미션 상태 변경
     * - status: 변경할 상태 (null이면 변경 안함)
     */
    fun updateMission(missionId: Long, status: MissionStatus?): MemberMission {
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

        stored.updatedAt = LocalDateTime.now()

        return memberMissionRepository.save(stored)
    }


    fun getMission(id : Long) : MemberMission? {
        return memberMissionRepository.findByIdOrNull(id)
    }

    fun getTodayMissionsByMemberId(memberId: Long): List<MemberMission> {
       return memberMissionRepository.getTodayMissionsByMemberId(memberId)
    }

    /**
     * 사용자가 선택한 미션들을 ACTIVE 상태로 변경
     *
     * @param memberId 사용자 ID
     * @param memberMissionSelectionRequest 미션 선택 요청
     * @return 업데이트된 member_mission ID 목록
     */
    @Transactional
    fun saveMissions(memberId: Long, memberMissionSelectionRequest: MemberMissionSelectionRequest): List<Long> {
        val updatedMemberMissionIds = mutableListOf<Long>()
        val today = LocalDate.now()

        memberMissionSelectionRequest.missions.forEach { dto ->

            // missionId가 mission_embeddings 테이블에 존재하는지 확인
            if (!missionEmbeddingRepository.existsById(dto.missionId)) {
                throw IllegalArgumentException("missionId에 해당하는 미션을 찾을 수 없습니다: missionId=${dto.missionId}")
            }

            runBlocking {
                missionEmbeddingService.generateAndUpdateEmbedding(dto.missionId)
            }

            // memberId, memberInterestId, missionId로 기존 데이터 조회
            val memberMission = memberMissionRepository.findByMemberIdAndMemberInterestIdAndMissionIdAndDeletedFalse(
                memberId = memberId,
                memberInterestId = dto.memberInterestId,
                missionId = dto.missionId
            )

            if (memberMission == null) {
                throw IllegalArgumentException(
                    "등록된 미션을 찾을 수 없습니다: memberId=$memberId, memberInterestId=${dto.memberInterestId}, missionId=${dto.missionId}"
                )
            }

            // missionStatus를 ACTIVE로, targetDate를 오늘로 변경
            memberMission.missionStatus = MissionStatus.ACTIVE
            memberMission.targetDate = today
            memberMission.updatedAt = LocalDateTime.now()

            val saved = memberMissionRepository.save(memberMission)
            saved.id?.let { updatedMemberMissionIds.add(it) }
            logger.info("미션 활성화 완료: memberId=$memberId, memberInterestId=${dto.memberInterestId}, missionId=${dto.missionId}")
        }

        logger.info("미션 선택 완료 - 업데이트된 개수: ${updatedMemberMissionIds.size}")

        return updatedMemberMissionIds
    }
}