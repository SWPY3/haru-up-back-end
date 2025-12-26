package com.haruUp.mission.application

import com.haruUp.mission.domain.MissionRecommendResult
import com.haruUp.missionembedding.dto.MissionRecommendationResponse
import kotlinx.coroutines.runBlocking
import org.springframework.stereotype.Component
import java.time.LocalDate

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
     * @param targetDate 조회할 날짜
     * @return 추천된 미션 목록
     */
    fun recommendToday(
        memberId: Long,
        memberInterestId: Long,
        targetDate: LocalDate
    ): MissionRecommendResult {
        return missionRecommendService.recommend(memberId, memberInterestId, targetDate)
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

    /**
     * 재추천 횟수 초기화
     *
     * @param memberId 멤버 ID
     * @return 초기화 성공 여부
     */
    fun resetRetryCount(memberId: Long): Boolean {
        return missionRecommendService.resetRetryCount(memberId)
    }
}
