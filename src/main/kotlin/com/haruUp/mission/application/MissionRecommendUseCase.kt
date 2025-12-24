package com.haruUp.mission.application

import com.haruUp.mission.domain.MissionRecommendResult
import com.haruUp.missionembedding.dto.MissionRecommendationResponse
import kotlinx.coroutines.runBlocking
import org.springframework.stereotype.Component

/**
 * 미션 추천 UseCase
 */
@Component
class MissionRecommendUseCase(
    private val missionRecommendService: MissionRecommendService,
    private val memberMissionService: MemberMissionService
) {

    /**
     * 멤버 관심사 ID 목록 기반 미션 추천
     *
     * @param memberId 멤버 ID
     * @param memberInterestIds 멤버 관심사 ID 목록
     * @return 추천된 미션 응답
     */
    fun recommendByMemberInterestIds(
        memberId: Long,
        memberInterestIds: List<Long>
    ): MissionRecommendationResponse {
        return missionRecommendService.recommendByMemberInterestIds(memberId, memberInterestIds)
    }

    /**
     * 미션 선택
     *
     * @param memberId 멤버 ID
     * @param memberMissionIds 선택한 member_mission ID 목록
     * @return 저장된 미션 ID 목록
     */
    fun memberMissionSelection(
        memberId: Long,
        memberMissionIds: List<Long>
    ): List<Long> {
        return memberMissionService.saveMissions(memberId, memberMissionIds)
    }

    /**
     * 오늘의 미션 추천 조회
     *
     * @param memberId 멤버 ID
     * @param memberInterestId 멤버 관심사 ID
     * @return 추천된 미션 목록
     */
    fun recommendToday(
        memberId: Long,
        memberInterestId: Long
    ): MissionRecommendResult {
        return missionRecommendService.recommend(memberId, memberInterestId)
    }

    /**
     * 오늘의 미션 재추천
     *
     * @param memberId 멤버 ID
     * @param memberInterestId 멤버 관심사 ID
     * @param excludeMemberMissionIds 제외할 미션 ID 목록
     * @return 추천된 미션 응답
     */
    fun retryRecommend(
        memberId: Long,
        memberInterestId: Long,
        excludeMemberMissionIds: List<Long>?
    ): MissionRecommendationResponse {
        return runBlocking {
            missionRecommendService.retryWithInterest(memberId, memberInterestId, excludeMemberMissionIds)
        }
    }
}
