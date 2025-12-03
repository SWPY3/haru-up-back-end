package com.haruUp.member.controller

import com.haruUp.global.common.ApiResponse
import com.haruUp.member.application.useCase.MemberAuthUseCase
import com.haruUp.member.domain.dto.MemberDto
import com.haruUp.member.domain.type.LoginType
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/member/auth")
@Tag(name = "Member", description = "회원 인증 API")
class MemberAuthController(
    private val memberAuthUseCase: MemberAuthUseCase,
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
        val result = memberAuthUseCase.signUp(memberDto)
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
        val result = memberAuthUseCase.login(memberDto)
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
        val result = memberAuthUseCase.login(memberDto)
        return ApiResponse.success(result)
    }

    @Operation(summary = "로그아웃 (리프레시 토큰 폐기)")
    @PostMapping("/logout")
    fun logout(@RequestHeader("jwt-token") refreshToken: String): ApiResponse<String> {
        // 여기서 받는 jwt-token은 refreshToken이라고 보는 게 자연스러움
        memberAuthUseCase.logout(refreshToken)
        return ApiResponse.success("로그아웃 되었습니다.")
    }

    @Operation(summary = "리프레시 토큰으로 자동 로그인 (앱 재실행용)")
    @PostMapping("/jwt-token-login")
    fun tokenLogin(@RequestHeader("jwt-token") refreshToken: String): ApiResponse<MemberDto> {
        // useCase.tokenLogin 은 refreshToken 기반으로 재인증/재발급 처리
        val result = memberAuthUseCase.tokenLogin(refreshToken)
        return ApiResponse.success(result)
    }

    @Operation(summary = "리프레시 토큰으로 AccessToken 재발급")
    @PostMapping("/refresh-token")
    fun refreshToken(@RequestHeader("jwt-token") refreshToken: String): ApiResponse<String?> {
        val newAccessToken = memberAuthUseCase.refresh(refreshToken)
        return ApiResponse.success(newAccessToken)
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
