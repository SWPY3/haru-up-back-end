package com.haruUp.member.controller

import com.haruUp.global.common.ApiResponse
import com.haruUp.member.application.useCase.MemberUseCase
import com.haruUp.member.domain.dto.MemberDto
import com.haruUp.member.domain.dto.MemberProfileDto
import com.haruUp.member.domain.dto.MemberSettingDto
import com.haruUp.member.domain.type.LoginType
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/members")
@Tag(name = "Member", description = "회원 관리 API")
class MemberController(
    private val memberUseCase: MemberUseCase
) {

    // =====================
    // 인증 / 토큰 관련
    // =====================

    @Operation(summary = "회원가입")
    @PostMapping
    fun signUp(@RequestBody request: SignUpRequest): ApiResponse<MemberDto> {
        val memberDto = MemberDto(
            email = request.email,
            password = request.password,
            name = request.name,
            loginType = LoginType.COMMON
        )
        val result = memberUseCase.signUp(memberDto)
        return ApiResponse.success(result)
    }

    @Operation(summary = "일반 로그인")
    @PostMapping("/login")
    fun login(@RequestBody request: LoginRequest): ApiResponse<MemberDto> {
        val memberDto = MemberDto(
            email = request.email,
            password = request.password,
            loginType = LoginType.COMMON
        )
        val result = memberUseCase.login(memberDto)
        return ApiResponse.success(result)
    }

    @Operation(summary = "SNS 로그인")
    @PostMapping("/sns-login")
    fun snsLogin(@RequestBody request: SnsLoginRequest): ApiResponse<MemberDto> {
        val memberDto = MemberDto(
            email = request.email,
            name = request.name,
            loginType = request.loginType,
            snsId = request.snsId
        )
        val result = memberUseCase.login(memberDto)
        return ApiResponse.success(result)
    }

    @Operation(summary = "로그아웃")
    @PostMapping("/logout")
    fun logout(@RequestHeader("jwt-token") refreshToken: String): ApiResponse<String> {
        memberUseCase.logout(refreshToken)
        return ApiResponse.success("로그아웃 되었습니다.")
    }

    @Operation(summary = "jwt token으로 로그인")
    @PostMapping("/jwt-token-login")
    fun tokenLogin(@RequestHeader("jwt-token") accessToken: String): ApiResponse<MemberDto> {
        val result = memberUseCase.tokenLogin(accessToken)
        return ApiResponse.success(result)
    }

    @Operation(summary = "jwt token 재발급")
    @PostMapping("/refresh-token")
    fun refreshToken(@RequestHeader("jwt-token") refreshToken: String): ApiResponse<MemberDto> {
        val newToken = memberUseCase.refresh(refreshToken)
        return ApiResponse.success(newToken)
    }

    // =====================
    // 비밀번호 / 계정
    // =====================

    @Operation(summary = "이메일 중복 검사")
    @GetMapping("/email-check")
    fun checkEmailDuplication(@RequestParam email: String): ApiResponse<Boolean> {
        val isDuplicate = memberUseCase.isEmailDuplicate(email)
        return ApiResponse.success(isDuplicate)
    }

    @Operation(summary =  "이메일 수정")
    @GetMapping("/email-update")
    fun updateEmail( @RequestHeader("jwt-token") accessToken: String, @RequestParam newEmail: String
    ): ApiResponse<MemberDto> {
        val member = memberUseCase.tokenLogin(accessToken)
        val memberId = requireNotNull(member.id) { "토큰 정보에 회원 ID가 없습니다." }

        val updatedMember = memberUseCase.changeEmail(
            memberId = memberId,
            newEmail = newEmail
        )

        return ApiResponse.success(updatedMember)
    }

    @Operation(summary = "비밀번호 수정")
    @PutMapping("/password")
    fun changePassword(
        @RequestHeader("jwt-token") accessToken: String,
        @RequestBody request: ChangePasswordRequest
    ): ApiResponse<String> {
        val member = memberUseCase.tokenLogin(accessToken)
        val memberId = requireNotNull(member.id) { "토큰 정보에 회원 ID가 없습니다." }

        // 보통 현재 비밀번호는 토큰이 아니라 요청 바디에서 받는 구조가 자연스러움
        memberUseCase.changePassword(
            memberId = memberId,
            currentPassword = request.currentPassword,
            newPassword = request.newPassword
        )

        return ApiResponse.success("비밀번호가 성공적으로 변경되었습니다.")
    }

    // =====================
    // 프로필
    // =====================

    @Operation(summary = "회원 프로필 조회")
    @GetMapping("/profile")
    fun getProfile(
        @RequestHeader("jwt-token") accessToken: String
    ): ApiResponse<MemberProfileDto> {
        val member = memberUseCase.tokenLogin(accessToken)
        val memberId = requireNotNull(member.id) { "토큰 정보에 회원 ID가 없습니다." }

        val profile = memberUseCase.getMyProfile(memberId)
        return ApiResponse.success(profile)
    }

    @Operation(summary = "회원 프로필 수정")
    @PutMapping("/profile")
    fun updateProfile(
        @RequestHeader("jwt-token") accessToken: String,
        @RequestBody request: MemberProfileDto
    ): ApiResponse<MemberProfileDto> {
        val member = memberUseCase.tokenLogin(accessToken)
        val memberId = requireNotNull(member.id) { "토큰 정보에 회원 ID가 없습니다." }

        val updatedProfile = memberUseCase.updateMyProfile(memberId, request)
        return ApiResponse.success(updatedProfile)
    }

    @Operation(summary = "회원 세팅 조회")
    @GetMapping("/settings")
    fun getSettings( @RequestHeader("jwt-token") accessToken: String ): ApiResponse<MemberSettingDto>{
        val member = memberUseCase.tokenLogin(accessToken)
        val memberId = requireNotNull(member.id) { "토큰 정보에 회원 ID가 없습니다." }

        val settings = memberUseCase.getMySetting(memberId)
        return ApiResponse.success(settings)
    }

    @Operation(summary = "회원 세팅 수정")
    @PutMapping("/update-settings")
    fun updateSettings( @RequestHeader("jwt-token") accessToken: String, @RequestBody request: MemberSettingDto ): ApiResponse<MemberSettingDto> {
        val member = memberUseCase.tokenLogin(accessToken)
        val memberId = requireNotNull(member.id) { "토큰 정보에 회원 ID가 없습니다." }

        val updatedSettings = memberUseCase.updateMySetting(memberId, request)
        return ApiResponse.success(updatedSettings)
    }


    @Operation(summary = "회원 탈퇴")
    @DeleteMapping
    fun withdraw(
        @RequestHeader("jwt-token") accessToken: String,
        @RequestBody request: WithdrawRequest
    ): ApiResponse<String> {
        val member = memberUseCase.tokenLogin(accessToken)
        val memberId = requireNotNull(member.id) { "토큰 정보에 회원 ID가 없습니다." }

        // 탈퇴 시에도 비밀번호는 요청에서 받는 편이 일반적
        memberUseCase.withdraw(memberId, request.password)

        return ApiResponse.success("회원 탈퇴가 성공적으로 처리되었습니다.")
    }

}

// =====================
// Request DTOs
// =====================

data class SignUpRequest(
    val email: String,
    val password: String,
    val name: String
)

data class LoginRequest(
    val email: String,
    val password: String
)

// 카카오/구글 SDK에서 검증 완료된 값들을 보내준다고 가정
data class SnsLoginRequest(
    val loginType: LoginType, // KAKAO / GOOGLE / ...
    val snsId: String,
    val email: String?,
    val name: String
)

data class ChangePasswordRequest(
    val currentPassword: String,
    val newPassword: String
)

data class WithdrawRequest(
    val password: String
)
