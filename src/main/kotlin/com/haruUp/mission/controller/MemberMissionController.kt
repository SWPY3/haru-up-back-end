package com.haruUp.mission.controller

import com.haruUp.global.common.ApiResponse
import com.haruUp.global.security.MemberPrincipal
import com.haruUp.mission.application.MemberMissionUseCase
import com.haruUp.mission.application.MissionRecommendUseCase
import com.haruUp.mission.domain.MemberMissionDto
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import kotlin.apply

@RestController
@RequestMapping("/api/member/mission")
class MemberMissionController(
    private val memberMissionUseCase: MemberMissionUseCase,
    private val missionRecommendUseCase: MissionRecommendUseCase
) {

    /**
     * 오늘의 미션 목록 조회
     */
    @GetMapping
    fun todayMissions(
        @AuthenticationPrincipal principal: MemberPrincipal
    ): ApiResponse<List<MemberMissionDto>> {

        return ApiResponse.success(
            memberMissionUseCase.missionTodayList(principal.id)
        )
    }

    /**
     * 미션 상태 변경 (ACTIVE / COMPLETED / POSTPONED / INACTIVE)
     */
    @PostMapping("/status")
    fun changeMissionStatus(
        @AuthenticationPrincipal principal: MemberPrincipal,
        @RequestBody memberMissionDto: MemberMissionDto
    ): ApiResponse<Any> {

        memberMissionUseCase.missionChangeStatus(
            memberMissionDto.apply { this.memberId = principal.id }
        )

        return ApiResponse.success("OK")
    }

    /**
     * 오늘의 미션 추천
     */
    @GetMapping("/recommend")
    fun recommend(
        @AuthenticationPrincipal principal: MemberPrincipal
    ) = ApiResponse.success(
        missionRecommendUseCase.recommendToday(principal.id)
    )

    /**
     * 오늘의 미션 재추천
     */
    @PostMapping("/recommend/retry")
    fun retryRecommend(
        @AuthenticationPrincipal principal: MemberPrincipal
    ) = ApiResponse.success(
        missionRecommendUseCase.retryRecommend(principal.id)
    )
}