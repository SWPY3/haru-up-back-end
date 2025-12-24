package com.haruUp.interest.useCase

import com.haruUp.interest.service.MemberInterestSaveResult
import com.haruUp.interest.service.MemberInterestService
import org.springframework.stereotype.Component

/**
 * 멤버 관심사 UseCase
 */
@Component
class MemberInterestUseCase(
    private val memberInterestService: MemberInterestService
) {

    /**
     * 멤버 관심사 저장
     *
     * @param memberId 멤버 ID
     * @param interestIds 저장할 관심사 ID 목록
     * @return 저장 결과
     */
    fun saveInterests(memberId: Long, interestIds: List<Long>): MemberInterestSaveResult {
        val interestsWithPaths = interestIds.map { Pair(it, null as List<String>?) }
        return memberInterestService.saveInterests(memberId, interestsWithPaths)
    }
}
