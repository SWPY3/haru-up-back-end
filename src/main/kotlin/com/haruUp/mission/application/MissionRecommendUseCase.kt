package com.haruUp.mission.application

import com.haruUp.mission.domain.MemberMissionSelectionRequest
import com.haruUp.mission.domain.MissionRecommendResult
import com.haruUp.missionembedding.dto.MissionRecommendationResponse
import org.springframework.stereotype.Component

@Component
class MissionRecommendUseCase(
    private val missionRecommendService: MissionRecommendService,
    private val memberMissionService: MemberMissionService
) {

    /**
     * 오늘의 미션 추천 (memberInterestId 기반)
     *
     * 사용자 프로필과 관심사 정보를 기반으로 미션 추천된 미션 보여주기
     */
    suspend fun recommendToday(memberId: Long, memberInterestId: Long): MissionRecommendResult {
        return missionRecommendService.recommend(memberId, memberInterestId)
    }

    /**
     * 오늘의 미션 재추천 (memberInterestId 기반)
     *
     * 사용자 프로필과 관심사 정보를 기반으로 미션 재추천
     */
    suspend fun retryRecommend(memberId: Long, memberInterestId: Long): MissionRecommendationResponse {
        return missionRecommendService.retryWithInterest(memberId, memberInterestId)
    }

    fun memberMissionSelection(memberId: Long, memberMissionSelectionRequest: MemberMissionSelectionRequest): List<Long>{
        return memberMissionService.saveMissions(memberId, memberMissionSelectionRequest)
    }
}