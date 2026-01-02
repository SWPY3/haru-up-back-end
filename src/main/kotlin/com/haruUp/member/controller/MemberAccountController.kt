package com.haruUp.member.controller

import com.haruUp.global.common.ApiResponse
import com.haruUp.global.security.MemberPrincipal
import com.haruUp.member.application.useCase.MemberAccountUseCase
import com.haruUp.member.domain.dto.HomeMemberInfoDto
import com.haruUp.member.domain.dto.MemberDto
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/member/account")
@Tag(
    name = "Member",
    description = "회원 계정(이메일, 비밀번호, 탈퇴 등) 관리 API"
)
class MemberAccountController(
    private val memberAccountUseCase: MemberAccountUseCase,
) {

    // =====================
    // 회원 정보
    // =====================
    @Operation(
        summary = "내 계정 정보 조회",
        description = """
            현재 로그인한 회원의 계정 정보를 조회합니다.
            
            반환 정보 예시:
            - 회원 ID
            - 이메일
            - 계정 상태
            
            📌 인증된 사용자만 호출 가능합니다.
        """
    )
    @GetMapping("/me")
    fun findMe(
        @AuthenticationPrincipal principal: MemberPrincipal
    ): ApiResponse<MemberDto> {
        val member = memberAccountUseCase.findMemberById(principal.id)
        return ApiResponse.success(member)
    }

    // =====================
    // 이메일
    // =====================
    @Operation(
        summary = "이메일 중복 검사",
        description = """
            입력한 이메일이 이미 사용 중인지 확인하는 API입니다.
            
            - true  : 이미 사용 중인 이메일
            - false : 사용 가능한 이메일
            
            📌 회원 가입 또는 이메일 변경 전 검증 단계에서 사용됩니다.
        """
    )
    @PostMapping("/email/check")
    fun checkEmailDuplication(
        @RequestBody request: EmailCheckRequest
    ): ApiResponse<Boolean> {
        val isDuplicate = memberAccountUseCase.isEmailDuplicate(request.email)
        return ApiResponse.success(isDuplicate)
    }

    @Operation(
        summary = "이메일 변경",
        description = """
            회원 계정의 이메일을 변경합니다.
            
            - 새로운 이메일로 즉시 반영됩니다.
            - 이미 사용 중인 이메일인 경우 예외가 발생합니다.
            
            📌 보안 정책에 따라 재인증 절차가 추가될 수 있습니다.
        """
    )
    @PostMapping("/email/change")
    fun changeEmail(
        @AuthenticationPrincipal principal: MemberPrincipal,
        @RequestBody request: ChangeEmailRequest
    ): ApiResponse<MemberDto> {
        val updatedMember = memberAccountUseCase.changeEmail(
            memberId = principal.id,
            newEmail = request.newEmail
        )
        return ApiResponse.success(updatedMember)
    }

    // =====================
    // 비밀번호
    // =====================
    @Operation(
        summary = "비밀번호 변경",
        description = """
            회원 계정의 비밀번호를 변경합니다.
            
            요청 조건:
            - 현재 비밀번호가 일치해야 합니다.
            - 새로운 비밀번호는 보안 정책을 충족해야 합니다.
            
            📌 비밀번호 변경 성공 시 즉시 적용됩니다.
        """
    )
    @PostMapping("/password/change")
    fun changePassword(
        @AuthenticationPrincipal principal: MemberPrincipal,
        @RequestBody request: ChangePasswordRequest
    ): ApiResponse<String> {
        memberAccountUseCase.changePassword(
            memberId = principal.id,
            currentPassword = request.currentPassword,
            newPassword = request.newPassword
        )
        return ApiResponse.success("비밀번호가 성공적으로 변경되었습니다.")
    }

    // =====================
    // 회원 탈퇴
    // =====================
    @Operation(
        summary = "회원 탈퇴",
        description = """
            회원 계정을 탈퇴 처리하는 API입니다.
            
            요청 조건:
            - 현재 사용 중인 비밀번호를 입력해야 합니다.
            
            처리 내용:
            - 계정 상태를 탈퇴 처리합니다.
            - 이후 동일 계정으로 로그인이 불가능합니다.
            
            ⚠️ 탈퇴 처리 후에는 복구가 제한될 수 있습니다.
        """
    )
    @PostMapping("/withdraw")
    fun withdraw(
        @AuthenticationPrincipal principal: MemberPrincipal,
        @RequestBody request: WithdrawRequest
    ): ApiResponse<String> {
        memberAccountUseCase.withdraw(principal.id, request.password)
        return ApiResponse.success("회원 탈퇴가 성공적으로 처리되었습니다.")
    }

    @PostMapping("/home/memberInfo")
    fun homeMemberInfo(
        @AuthenticationPrincipal principal: MemberPrincipal,
    ) : ApiResponse<List<HomeMemberInfoDto>> {
        val homeMemberInfo = memberAccountUseCase.homeMemberInfo(principal.id);

        return ApiResponse.success(homeMemberInfo)

    }

    // =====================
    // Request DTO
    // =====================
    data class EmailCheckRequest(
        val email: String
    )

    data class ChangeEmailRequest(
        val newEmail: String
    )

    data class ChangePasswordRequest(
        val currentPassword: String,
        val newPassword: String
    )

    data class WithdrawRequest(
        val password: String
    )

}

