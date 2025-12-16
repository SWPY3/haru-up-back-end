package com.haruUp.member.controller

import com.haruUp.character.domain.dto.CharacterDto
import com.haruUp.global.common.ApiResponse
import com.haruUp.global.error.BusinessException
import com.haruUp.global.error.ErrorCode
import com.haruUp.global.security.MemberPrincipal
import com.haruUp.member.application.useCase.MemberProfileUseCase
import com.haruUp.member.domain.dto.MemberProfileDto
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
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

    @Operation(summary = "프로필, 캐릭터 CREATE",
        description = """
            캐릭터 선택후 프로필 입력완료 시점에 호출하기
        """
    )
    @PostMapping("/add_profile")
    fun createDefaultProfile(
        @AuthenticationPrincipal principal : MemberPrincipal,
        @RequestBody characterDto : CharacterDto
    ) : ApiResponse<String>{
        var characterId: Long? = characterDto.id
        if (characterId == null) {
            throw BusinessException(ErrorCode.NOT_FOUND, "캐릭터를 찾을 수 없습니다.")
        }

        memberProfileUseCase.createDefaulProfile(principal.id, characterId)

        return ApiResponse.success( "OK")
    }



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
