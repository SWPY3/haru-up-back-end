package com.haruUp.interest.service

import com.haruUp.interest.dto.MemberInterestDto
import com.haruUp.interest.entity.InterestType
import com.haruUp.interest.entity.MemberInterestEntity
import com.haruUp.interest.repository.InterestEmbeddingJpaRepository
import com.haruUp.interest.repository.MemberInterestJpaRepository
import jakarta.transaction.Transactional
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
        interests: List<Pair<Long, List<String>?>>,
        interestType: InterestType = InterestType.PRIMARY
    ): MemberInterestSaveResult {
        // PRIMARY 관심사 1개 제한
        if (interestType == InterestType.PRIMARY) {
            val primaryCount = memberInterestRepository
                .countByMemberIdAndInterestTypeAndDeletedFalse(memberId, InterestType.PRIMARY)
            require(primaryCount == 0L) { "메인 관심사는 최대 1개만 등록 가능합니다." }
            require(interests.size == 1) { "메인 관심사는 1개만 등록할 수 있습니다." }
        }

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
                directFullPath = pathToSave,
                interestType = interestType
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

    /**
     * 서브 관심사 추가
     * - 메인 관심사가 이미 존재해야 함
     * - 서브 관심사는 최대 1개만 등록 가능
     * - 메인 관심사와 동일한 관심사 등록 불가
     */
    @Transactional
    fun addSubInterest(
        memberId: Long,
        interestId: Long,
        directFullPath: List<String>?
    ): MemberInterestSaveResult {
        // 1. 메인 관심사 존재 확인
        val primaryInterests = memberInterestRepository
            .findByMemberIdAndInterestTypeAndDeletedFalse(memberId, InterestType.PRIMARY)
        require(primaryInterests.isNotEmpty()) { "메인 관심사가 등록되어 있어야 합니다." }

        // 2. 서브 관심사 중복 확인 (최대 1개)
        val subCount = memberInterestRepository
            .countByMemberIdAndInterestTypeAndDeletedFalse(memberId, InterestType.SUB)
        require(subCount == 0L) { "서브 관심사는 최대 1개만 등록 가능합니다." }

        // 3. 메인 관심사와 동일한 관심사 등록 불가
        val primaryInterestIds = primaryInterests.map { it.interestId }
        require(interestId !in primaryInterestIds) {
            "메인 관심사와 동일한 관심사를 서브로 등록할 수 없습니다."
        }

        // 4. 저장
        return saveInterests(
            memberId = memberId,
            interests = listOf(Pair(interestId, directFullPath)),
            interestType = InterestType.SUB
        )
    }

    /**
     * 멤버 관심사 존재 여부 확인
     */
    fun hasInterests(memberId: Long): Boolean {
        return memberInterestRepository.findByMemberIdAndDeletedFalse(memberId).isNotEmpty()
    }

    @Transactional
    fun deleteMemberInterestsByMemberId(memberId: Long) {
        memberInterestRepository.softDeleteAllByMemberId(memberId)
        logger.info("멤버 관심사 삭제 완료 - memberId: $memberId")
    }

    @Transactional
    fun selectMemberInterestsByMemberId(memberId : Long) : List<List<String>?> {
        val memberInterests = memberInterestRepository.findAllByMemberIdAndDeletedFalse(memberId)

        val fullPathList = memberInterests
            .stream()
            .map { it.directFullPath }
            .toList()

        println("fullPathList: $fullPathList")

        return fullPathList
    }
}
