package com.haruUp.member.controller

import com.haruUp.global.common.ApiResponse
import com.haruUp.global.security.MemberPrincipal
import com.haruUp.member.application.useCase.MemberSettingUseCase
import com.haruUp.member.domain.dto.MemberSettingDto
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/member/setting")
@Tag(name = "Member", description = "회원 설정 관리 API")
class MemberSettingController(
    private val memberSettingUseCase: MemberSettingUseCase
) {

    @Operation(summary = "회원 세팅 조회")
    @GetMapping("/settings")
    fun getSettings( @AuthenticationPrincipal principal: MemberPrincipal ): ApiResponse<MemberSettingDto> {
        val memberId = principal.id
        val settings = memberSettingUseCase.getMySetting(memberId)
        return ApiResponse.success(settings)
    }

    @Operation(summary = "회원 세팅 수정")
    @PutMapping("/update-settings")
    fun updateSettings( @AuthenticationPrincipal principal: MemberPrincipal, @RequestBody request: MemberSettingDto ): ApiResponse<MemberSettingDto> {
        val memberId = principal.id
        val updatedSettings = memberSettingUseCase.updateMySetting(memberId, request)
        return ApiResponse.success(updatedSettings)
    }

    @Operation(summary = "회원 미션 리마인더 조회")
    @GetMapping("/mission-reminder")
    fun getMissionReminder(
        @AuthenticationPrincipal principal: MemberPrincipal
    ): ApiResponse<MemberSettingDto> {
        val memberId = principal.id
        val settings = memberSettingUseCase.getMySetting(memberId)
        return ApiResponse.success(settings)
    }
}
