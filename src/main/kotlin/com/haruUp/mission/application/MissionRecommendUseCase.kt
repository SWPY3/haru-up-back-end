package com.haruUp.mission.application

import com.haruUp.mission.domain.MissionRecommendResult
import org.springframework.stereotype.Component

@Component
class MissionRecommendUseCase(
    private val missionRecommendService: MissionRecommendService
) {

    /**
     * 오늘의 미션 추천
     */
    fun recommendToday(memberId: Long): MissionRecommendResult {
        return missionRecommendService.recommend(memberId)
    }

    /**
     * 오늘의 미션 재추천
     */
    fun retryRecommend(memberId: Long): MissionRecommendResult {
        return missionRecommendService.retry(memberId)
    }
}