package com.haruUp.member.controller

import com.haruUp.global.common.ApiResponse
import com.haruUp.global.security.MemberPrincipal
import com.haruUp.member.application.useCase.MemberProfileUseCase
import com.haruUp.member.domain.dto.MemberProfileDto
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/member/profile")
@Tag(name = "Member", description = "회원 프로필 관리 API")
class MemberProfileController(
    private val memberProfileUseCase: MemberProfileUseCase,
) {

    // =====================
    // 프로필
    // =====================

    @Operation(summary = "회원 프로필 조회")
    @GetMapping("/profile")
    fun getProfile( @AuthenticationPrincipal principal: MemberPrincipal ): ApiResponse<MemberProfileDto> {
        val memberId = principal.id
        val profile = memberProfileUseCase.getMyProfile(memberId)
        return ApiResponse.success(profile)
    }

    @Operation(summary = "회원 프로필 수정")
    @PutMapping("/profile")
    fun updateProfile( @AuthenticationPrincipal principal: MemberPrincipal, @RequestBody request: MemberProfileDto ): ApiResponse<MemberProfileDto> {
        val memberId = principal.id
        val updatedProfile = memberProfileUseCase.updateMyProfile(memberId, request)
        return ApiResponse.success(updatedProfile)
    }
}
