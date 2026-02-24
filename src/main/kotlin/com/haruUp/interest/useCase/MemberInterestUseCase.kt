package com.haruUp.interest.useCase

import com.haruUp.interest.entity.InterestType
import com.haruUp.interest.repository.MemberInterestJpaRepository
import com.haruUp.interest.service.MemberInterestSaveResult
import com.haruUp.interest.service.MemberInterestService
import com.haruUp.mission.application.MemberMissionService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 멤버 관심사 UseCase
 */
@Component
class MemberInterestUseCase(
    private val memberInterestService: MemberInterestService,
    private val memberInterestRepository: MemberInterestJpaRepository,
    private val memberMissionService: MemberMissionService
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 멤버 관심사 저장
     *
     * @param memberId 멤버 ID
     * @param interests (interestId, directFullPath) 목록
     * @param interestType 관심사 타입 (PRIMARY or SUB)
     * @return 저장 결과
     */
    fun saveInterests(
        memberId: Long,
        interests: List<Pair<Long, List<String>?>>,
        interestType: InterestType = InterestType.PRIMARY
    ): MemberInterestSaveResult {
        return if (interestType == InterestType.SUB) {
            require(interests.size == 1) { "서브 관심사는 1개만 추가할 수 있습니다." }
            val (interestId, directFullPath) = interests.first()
            memberInterestService.addSubInterest(memberId, interestId, directFullPath)
        } else {
            memberInterestService.saveInterests(memberId, interests)
        }
    }

    /**
     * 멤버 관심사 삭제 (관심사 + 관련 미션 soft delete)
     *
     * @param memberId 멤버 ID
     * @param memberInterestId 멤버 관심사 ID
     */
    @Transactional
    fun deleteInterest(memberId: Long, memberInterestId: Long) {
        val memberInterest = memberInterestRepository.findByIdAndMemberIdAndDeletedFalse(
            id = memberInterestId,
            memberId = memberId
        ) ?: throw IllegalArgumentException("관심사를 찾을 수 없습니다. memberInterestId: $memberInterestId")

        // 1. 관련 미션 soft delete
        val deletedMissionCount = memberMissionService.deleteMissionsByMemberInterestId(memberId, memberInterestId)
        logger.info("관심사 미션 삭제 - memberId: $memberId, memberInterestId: $memberInterestId, 삭제된 미션: $deletedMissionCount")

        // 2. 관심사 soft delete
        memberInterestRepository.softDeleteByIdAndMemberId(memberInterestId, memberId)
        logger.info("관심사 삭제 완료 - memberId: $memberId, memberInterestId: $memberInterestId, type: ${memberInterest.interestType}")
    }
}
