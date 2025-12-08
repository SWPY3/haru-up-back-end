package com.haruUp.member.controller

import com.haruUp.global.common.ApiResponse
import com.haruUp.member.application.useCase.MemberAuthUseCase
import com.haruUp.member.domain.dto.MemberDto
import com.haruUp.member.domain.type.LoginType
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/member/auth")
@Tag(name = "Member", description = "회원 인증 API")
class MemberAuthController(
    private val memberAuthUseCase: MemberAuthUseCase,
) {

    // =====================
    // COMMON 인증 (예비용 / 내부용)
    // =====================

//    @Operation(
//        summary = "회원가입 (COMMON)",
//        description = """
//            이메일 + 비밀번호 기반의 일반 회원가입입니다.
//            현재 서비스에서는 주로 SNS 로그인(KAKAO/APPLE 등)을 사용하지만,
//            추후 어드민 계정 / 내부 계정 등에 활용할 수 있습니다.
//        """
//    )
//    @PostMapping
//    fun signUp(@RequestBody request: SignUpRequest): ApiResponse<MemberDto> {
//        val memberDto = MemberDto(
//            email = request.email,
//            password = request.password,
//            name = request.name,
//            loginType = LoginType.COMMON
//        )
//        val result = memberAuthUseCase.signUp(memberDto)
//        return ApiResponse.success(result)
//    }
//
//    @Operation(
//        summary = "일반 로그인 (COMMON)",
//        description = """
//            이메일 + 비밀번호 기반의 일반 로그인입니다.
//            현재 앱 클라이언트에서는 거의 사용하지 않고,
//            주로 테스트 / 내부용으로 사용할 수 있습니다.
//        """
//    )
//    @PostMapping("/login")
//    fun login(@RequestBody request: LoginRequest): ApiResponse<MemberDto> {
//        val memberDto = MemberDto(
//            email = request.email,
//            password = request.password,
//            loginType = LoginType.COMMON
//        )
//        val result = memberAuthUseCase.login(memberDto)
//        return ApiResponse.success(result)
//    }

    // =====================
    // SNS 인증
    // =====================

    @Operation(
        summary = "SNS 로그인",
        description = """
            카카오/애플/구글 등의 SNS 로그인을 처리하는 엔드포인트입니다.
            
            - 클라이언트(모바일 앱)에서 각 SNS SDK(OAuth)를 통해 **이미 인증을 완료한 상태**라고 가정합니다.
            - SDK에서 내려준 `snsId`, `email`, `name`, `loginType` 등을 전달하면,
              서버에서 해당 SNS 계정으로 가입된 회원을 조회하거나,
              없을 경우 자동으로 회원가입을 진행합니다.
            - 첫 로그인 시: 회원 + 기본 설정 + 토큰 세트가 생성됩니다.
            - 재 로그인 시: 기존 회원으로 로그인하며, 토큰 세트만 새로 발급됩니다.
        """
    )
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

    @Operation(summary = "프로필, 캐릭터 CREATE",
        description = """
            캐릭터 선택후 프로필 입력완료 시점에 호출하기
        """
        )
    @PostMapping("/add_profile")
    fun createDefaultProfile(@AuthenticationPrincipal memberDto : MemberDto, characterId : Long) : ApiResponse<String>{


         memberAuthUseCase.createDefaulProfile(memberDto.id, characterId)

        return ApiResponse.success( "OK")
    }

    // =====================
    // 토큰 / 세션 관리
    // =====================

    @Operation(
        summary = "로그아웃 (리프레시 토큰 폐기)",
        description = """
            리프레시 토큰 기반 로그아웃 API입니다.
            
            - 요청 헤더 `jwt-token`에 **리프레시 토큰**을 담아 전송합니다.
            - 서버는 다음 작업을 수행합니다:
              1) 해당 리프레시 토큰을 데이터베이스에서 revoke 처리
              2) Redis에 저장된 세션 키 제거 (예: auth:refresh:{refreshToken})
            - 이후 동일 리프레시 토큰으로는 더 이상 재로그인/재발급이 불가능합니다.
        """
    )
    @PostMapping("/logout")
    fun logout(@RequestHeader("jwt-token") refreshToken: String): ApiResponse<String> {
        memberAuthUseCase.logout(refreshToken)
        return ApiResponse.success("로그아웃 되었습니다.")
    }

    @Operation(
        summary = "리프레시 토큰으로 자동 로그인",
        description = """
            자동 로그인 시 사용되는 엔드포인트입니다.
            
            - 요청 헤더 `jwt-token`에 **토큰**을 담아 전송합니다.
            - 서버는 내부적으로 다음 로직을 수행합니다:
              1) Redis에 해당 리프레시 토큰 세션이 존재하는지 확인
              2) 리프레시 토큰의 유효성 및 만료 여부 검사
              3) DB의 RefreshToken 엔티티 상태(revoked, 만료 등)를 검사
              4) 토큰에 포함된 memberId와 실제 저장된 memberId가 일치하는지 검증
              5) 새로운 AccessToken / RefreshToken 세트를 발급
            - 응답으로는:
              - 회원 정보(MemberDto)
              - 새 AccessToken
              - 새 RefreshToken
            을 함께 반환합니다.
            
            ※ 단순히 AccessToken만 필요할 때에도 이 API를 호출한 뒤,
               클라이언트에서 accessToken 필드만 사용해도 됩니다.
        """
    )
    @PostMapping("/jwt-token-login")
    fun tokenLogin(@RequestHeader("jwt-token") refreshToken: String): ApiResponse<MemberDto> {
        val result = memberAuthUseCase.tokenLogin(refreshToken)
        return ApiResponse.success(result)
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

// 카카오/구글/애플 SDK에서 검증 완료된 값들을 보내준다고 가정
data class SnsLoginRequest(
    val loginType: LoginType, // KAKAO / GOOGLE / APPLE / ...
    val snsId: String,
    val email: String?,        // 일부 SNS는 이메일을 내려주지 않을 수 있음
    val name: String
)