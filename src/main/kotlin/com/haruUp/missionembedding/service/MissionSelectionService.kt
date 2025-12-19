package com.haruUp.missionembedding.service

import com.haruUp.interest.model.InterestLevel
import com.haruUp.interest.repository.InterestEmbeddingJpaRepository
import com.haruUp.missionembedding.dto.MissionSelectionRequest
import com.haruUp.missionembedding.repository.MissionEmbeddingRepository
import com.haruUp.mission.domain.MemberMission
import com.haruUp.mission.domain.MissionStatus
import com.haruUp.mission.infrastructure.MemberMissionRepository
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 미션 선택 서비스
 *
 * 사용자가 선택한 미션의 임베딩을 생성하고,
 * member_mission 테이블에 사용자-미션 연결 저장
 */
@Service
class MissionSelectionService(
    private val missionEmbeddingService: MissionEmbeddingService,
    private val missionEmbeddingRepository: MissionEmbeddingRepository,
    private val memberMissionRepository: MemberMissionRepository,
    private val interestEmbeddingJpaRepository: InterestEmbeddingJpaRepository,
    private val memberInterestRepository: com.haruUp.interest.repository.MemberInterestJpaRepository
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 사용자가 선택한 미션들의 임베딩을 생성하고 사용자와 연결
     *
     * @param memberId 사용자 ID
     * @param request 미션 선택 요청
     * @return 저장된 미션 정보
     */
    @Transactional
    fun saveMissions(memberId: Long, request: MissionSelectionRequest): List<Long> {
        logger.info("미션 선택 요청 - 사용자: $memberId, 미션 개수: ${request.missions.size}")

        // interestId -> memberInterestId 매핑을 저장할 Map
        val interestToMemberInterestMap = mutableMapOf<Long, Long>()

        // 1. 먼저 모든 미션에 대해 validation 수행
        request.missions.forEach { dto ->
            if (dto.directFullPath.isEmpty()) {
                throw IllegalArgumentException("directFullPath가 비어있습니다: missionId=${dto.missionId}")
            }

            // missionId가 mission_embeddings 테이블에 존재하는지 확인
            if (!missionEmbeddingRepository.existsById(dto.missionId)) {
                throw IllegalArgumentException("missionId에 해당하는 미션을 찾을 수 없습니다: missionId=${dto.missionId}")
            }

            // interestId가 interest_embeddings에 존재하고 SUB 레벨인지 확인
            val interest = interestEmbeddingJpaRepository.findById(dto.interestId).orElse(null)
                ?: throw IllegalArgumentException("interestId에 해당하는 관심사를 찾을 수 없습니다: interestId=${dto.interestId}")

            if (interest.level != InterestLevel.SUB) {
                throw IllegalArgumentException("interestId는 소분류(SUB)만 사용 가능합니다: interestId=${dto.interestId}, level=${interest.level}")
            }

            // 사용자가 해당 관심사를 이미 등록했는지 확인 (member_interest 테이블)
            val memberInterest = memberInterestRepository.findByMemberIdAndInterestId(memberId, dto.interestId)
                ?: throw IllegalArgumentException("사용자가 해당 관심사를 등록하지 않았습니다: memberId=$memberId, interestId=${dto.interestId}")

            // memberInterestId를 Map에 저장
            interestToMemberInterestMap[dto.interestId] = memberInterest.id!!
        }

        // 2. validation 통과 후 저장 시작
        val savedMemberMissionIds = mutableListOf<Long>()

        request.missions.forEach { dto ->
            try {
                // 1. 미션 임베딩 생성 및 업데이트 (Clova API로 벡터 계산)
                runBlocking {
                    missionEmbeddingService.generateAndUpdateEmbedding(dto.missionId)
                }

                // 2. 사용자-미션 연결 저장
                val memberInterestId = interestToMemberInterestMap[dto.interestId]!!
                val memberMission = MemberMission(
                    memberId = memberId,
                    missionId = dto.missionId,
                    memberInterestId = memberInterestId,
                    missionStatus = MissionStatus.ACTIVE,
                    expEarned = 0
                )
                val saved = memberMissionRepository.save(memberMission)
                saved.id?.let { savedMemberMissionIds.add(it) }
                logger.info("사용자-미션 연결 저장 완료: 사용자=$memberId, missionId=${dto.missionId}, memberInterestId=$memberInterestId")
            } catch (e: Exception) {
                logger.error("미션 선택 실패: missionId=${dto.missionId}, 에러: ${e.message}", e)
            }
        }

        logger.info("미션 선택 완료 - 저장된 개수: ${savedMemberMissionIds.size}")

        return savedMemberMissionIds
    }

}
