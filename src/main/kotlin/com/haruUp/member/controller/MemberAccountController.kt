package com.haruUp.member.controller

import com.haruUp.global.common.ApiResponse
import com.haruUp.global.security.MemberPrincipal
import com.haruUp.member.application.useCase.MemberAccountUseCase
import com.haruUp.member.domain.dto.MemberDto
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/member/account")
@Tag(name = "Member", description = "회원 계정 관리 API")
class MemberAccountController(
    private val memberAccountUseCase: MemberAccountUseCase,
) {

    @Operation(summary = "CI/CD 테스트용 ")
    @GetMapping("/CICDTest")
    fun CICDTest( @AuthenticationPrincipal principal: MemberPrincipal ): ApiResponse<MemberDto> {
        val memberId = principal.id
        val member = memberAccountUseCase.findMemberById(memberId)
        return ApiResponse.success(member)
    }

    // =====================
    // 비밀번호 / 계정
    // =====================

    @Operation(summary = "사용자 조회")
    @GetMapping("/get-member")
    fun findMember( @AuthenticationPrincipal principal: MemberPrincipal ): ApiResponse<MemberDto> {
        val memberId = principal.id
        val member = memberAccountUseCase.findMemberById(memberId)
        return ApiResponse.success(member)
    }


    @Operation(summary = "이메일 중복 검사")
    @GetMapping("/email-check")
    fun checkEmailDuplication(@RequestParam email: String): ApiResponse<Boolean> {
        val isDuplicate = memberAccountUseCase.isEmailDuplicate(email)
        return ApiResponse.success(isDuplicate)
    }

    @Operation(summary = "이메일 수정")
    @GetMapping("/email-update")
    fun updateEmail( @AuthenticationPrincipal principal: MemberPrincipal, @RequestParam newEmail: String ): ApiResponse<MemberDto> {
        // JwtAuthenticationFilter에서 세팅해둔 인증 정보에서 memberId 추출
        val memberId = principal.id

        val updatedMember = memberAccountUseCase.changeEmail(
            memberId = memberId,
            newEmail = newEmail
        )

        return ApiResponse.success(updatedMember)
    }

    @Operation(summary = "비밀번호 수정")
    @PutMapping("/password")
    fun changePassword( @AuthenticationPrincipal principal: MemberPrincipal, @RequestBody request: ChangePasswordRequest ): ApiResponse<String> {
        val memberId = principal.id

        memberAccountUseCase.changePassword(
            memberId = memberId,
            currentPassword = request.currentPassword,
            newPassword = request.newPassword
        )

        return ApiResponse.success("비밀번호가 성공적으로 변경되었습니다.")
    }

    @Operation(summary = "회원 탈퇴")
    @DeleteMapping
    fun withdraw( @AuthenticationPrincipal principal: MemberPrincipal, @RequestBody request: WithdrawRequest ): ApiResponse<String> {
        val memberId = principal.id

        // 탈퇴 시에도 비밀번호는 요청에서 받는 편이 일반적
        memberAccountUseCase.withdraw(memberId, request.password)

        return ApiResponse.success("회원 탈퇴가 성공적으로 처리되었습니다.")
    }
}

// =====================
// Request DTOs
// =====================

data class ChangePasswordRequest(
    val currentPassword: String,
    val newPassword: String
)

data class WithdrawRequest(
    val password: String
)
