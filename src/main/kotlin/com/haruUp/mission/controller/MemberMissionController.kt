package com.haruUp.mission.controller

import com.haruUp.global.common.ApiResponse
import com.haruUp.global.security.MemberPrincipal
import com.haruUp.mission.application.MemberMissionUseCase
import com.haruUp.mission.domain.MemberMissionDto
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import kotlin.apply

@Controller
@RequestMapping("/api/member/mission")
class MemberMissionController (
    private val memberMissionUseCase: MemberMissionUseCase
) {


    @RequestMapping("/missionCompleted")
    fun missionCompleted(
        @AuthenticationPrincipal principal: MemberPrincipal,
        @RequestBody memberMissionDto : MemberMissionDto
    ) : ApiResponse<String> {

        memberMissionUseCase.missionChangeStatus( memberMissionDto.apply { this.memberId = principal.id } )

        return ApiResponse.success("OK");
    }

    // 미루기
    @RequestMapping("/missionPostEnd")
    fun missionPostEnd(
    @AuthenticationPrincipal principal: MemberPrincipal,
    @RequestBody memberMissionDto : MemberMissionDto
    ) : ApiResponse<String> {

        memberMissionUseCase.missionChangeStatus( memberMissionDto.apply { this.memberId = principal.id } )

        return ApiResponse.success("OK")
    }

    // 취소
    @RequestMapping("/missionInActive")
    fun missionInActive(
        @AuthenticationPrincipal principal: MemberPrincipal,
        @RequestBody memberMissionDto : MemberMissionDto
    ) : ApiResponse<String> {

        memberMissionUseCase.missionChangeStatus( memberMissionDto.apply { this.memberId = principal.id } )

        return ApiResponse.success("OK")

    }



}