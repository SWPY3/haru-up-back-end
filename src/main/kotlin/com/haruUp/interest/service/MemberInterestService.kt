package com.haruUp.interest.service

import com.haruUp.interest.entity.MemberInterestEntity
import com.haruUp.interest.repository.InterestEmbeddingJpaRepository
import com.haruUp.interest.repository.MemberInterestJpaRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * 멤버 관심사 저장 결과
 */
data class MemberInterestSaveResult(
    val savedIds: List<Long>,
    val invalidInterestIds: List<Long>,
    val savedCount: Int
) {
    val hasInvalidInterests: Boolean get() = invalidInterestIds.isNotEmpty()
}

/**
 * 멤버 관심사 Service
 */
@Service
class MemberInterestService(
    private val memberInterestRepository: MemberInterestJpaRepository,
    private val interestEmbeddingRepository: InterestEmbeddingJpaRepository
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 멤버 관심사 저장
     * - 소분류(SUB) 레벨의 관심사만 저장 가능
     * - 동일한 interestId도 매번 새로운 row로 저장
     * - directFullPath가 제공되면 해당 값 사용, 없으면 DB에서 조회
     *
     * @param memberId 멤버 ID
     * @param interests (interestId, directFullPath?) 쌍 목록
     * @return 저장 결과 (저장된 member_interest ID 목록, 잘못된 interestId 목록)
     */
    fun saveInterests(
        memberId: Long,
        interests: List<Pair<Long, List<String>?>>
    ): MemberInterestSaveResult {
        val savedIds = mutableListOf<Long>()
        val invalidInterestIds = mutableListOf<Long>()

        for ((interestId, directFullPath) in interests) {
            val interestEntity = interestEmbeddingRepository.findEntityById(interestId)

            if (interestEntity == null) {
                logger.warn("관심사를 찾을 수 없습니다: interestId=$interestId")
                continue
            }

            // 소분류(SUB) 레벨인지 확인 (fullPath.size == 3)
            if (interestEntity.fullPath.size != 3) {
                logger.warn("소분류(SUB) 레벨의 관심사만 저장 가능합니다: interestId=$interestId")
                invalidInterestIds.add(interestId)
                continue
            }

            // directFullPath가 제공되면 해당 값 사용, 없으면 DB에서 조회한 값 사용
            val pathToSave = directFullPath ?: interestEntity.fullPath

            val memberInterest = MemberInterestEntity(
                memberId = memberId,
                interestId = interestEntity.id!!,
                directFullPath = pathToSave
            )
            val saved = memberInterestRepository.save(memberInterest)
            saved.id?.let { savedIds.add(it) }
        }

        logger.info("멤버 관심사 저장 완료 - memberId: $memberId, savedCount: ${savedIds.size}")

        return MemberInterestSaveResult(
            savedIds = savedIds,
            invalidInterestIds = invalidInterestIds,
            savedCount = savedIds.size
        )
    }

    fun deleteMemberInterestsByMemberId(memberId: Long) {
        memberInterestRepository.softDeleteAllByMemberId(memberId)
        logger.info("멤버 관심사 삭제 완료 - memberId: $memberId")
    }
}
