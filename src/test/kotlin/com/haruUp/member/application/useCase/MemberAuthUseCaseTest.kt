package com.haruUp.member.application.useCase

import org.junit.jupiter.api.Assertions.*

import com.haruUp.auth.application.RefreshTokenService
import com.haruUp.global.error.BusinessException
import com.haruUp.global.error.ErrorCode
import com.haruUp.global.security.JwtTokenProvider
import com.haruUp.member.application.service.MemberService
import com.haruUp.member.application.service.MemberSettingService
import com.haruUp.member.application.service.MemberValidator
import com.haruUp.member.domain.Member
import com.haruUp.member.domain.dto.MemberDto
import com.haruUp.member.domain.dto.MemberSettingDto
import com.haruUp.member.domain.type.LoginType
import com.haruUp.member.domain.type.MemberStatus
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.Duration
import java.time.LocalDateTime
import java.util.Optional

// RefreshToken 엔티티 패키지는 실제 프로젝트에 맞게 조정
import com.haruUp.auth.domain.RefreshToken

@ExtendWith(MockitoExtension::class)
class MemberAuthUseCaseTest {

    @Mock
    lateinit var memberService: MemberService

    @Mock
    lateinit var memberSettingService: MemberSettingService

    @Mock
    lateinit var jwtTokenProvider: JwtTokenProvider

    @Mock
    lateinit var passwordEncoder: PasswordEncoder

    @Mock
    lateinit var memberValidator: MemberValidator

    @Mock
    lateinit var refreshTokenService: RefreshTokenService

    @Mock
    lateinit var stringRedisTemplate: StringRedisTemplate

    private lateinit var valueOps: ValueOperations<String, String>

    private lateinit var memberAuthUseCase: MemberAuthUseCase

    @BeforeEach
    fun setUp() {
        valueOps = mock()
        whenever(stringRedisTemplate.opsForValue()).thenReturn(valueOps)

        memberAuthUseCase = MemberAuthUseCase(
            memberService = memberService,
            memberSettingService = memberSettingService,
            jwtTokenProvider = jwtTokenProvider,
            passwordEncoder = passwordEncoder,
            memberValidator = memberValidator,
            refreshTokenService = refreshTokenService,
            stringRedisTemplate = stringRedisTemplate
        )
    }

    @Test
    fun `COMMON 회원가입 성공 시 비밀번호 암호화, 회원 저장, 기본 설정 생성이 호출되고 저장된 DTO를 반환한다`() {
        // given
        val requestDto = MemberDto(
            id = null,
            email = "user@test.com",
            password = "raw-password",
            name = "테스트유저",
            loginType = LoginType.COMMON,
            snsId = null,
            status = MemberStatus.ACTIVE
        )

        whenever(passwordEncoder.encode("raw-password"))
            .thenReturn("encoded-password")

        val savedDto = MemberDto(
            id = 1L,
            email = "user@test.com",
            password = "encoded-password",
            name = "테스트유저",
            loginType = LoginType.COMMON,
            snsId = null,
            status = MemberStatus.ACTIVE
        )

        whenever(memberService.addMember(any<Member>()))
            .thenReturn(savedDto)

        whenever(memberSettingService.createDefaultSetting(any()))
            .thenReturn(MemberSettingDto().apply { memberId = 1L })

        // when
        val result = memberAuthUseCase.signUp(requestDto)

        // then
        verify(memberValidator, times(1)).validateCommonSignUp(requestDto)
        verify(passwordEncoder, times(1)).encode("raw-password")
        verify(memberService, times(1)).addMember(any<Member>())
        verify(memberSettingService, times(1))
            .createDefaultSetting(check {
                assertEquals(1L, it.memberId)
            })

        // 토큰 관련 컴포넌트/Redis는 호출 안 됨
        verifyNoInteractions(jwtTokenProvider)
        verifyNoInteractions(refreshTokenService)
        verifyNoInteractions(stringRedisTemplate)

        assertEquals(1L, result.id)
        assertEquals("user@test.com", result.email)
        assertEquals("테스트유저", result.name)
        assertEquals(LoginType.COMMON, result.loginType)
        assertEquals("encoded-password", result.password)
    }

    @Test
    fun `COMMON 로그인 성공 시 validator로 회원 조회 후 토큰 발급 및 refreshToken을 DB와 Redis에 저장한다`() {
        // given
        val loginDto = MemberDto(
            email = "user@test.com",
            password = "raw-password",
            loginType = LoginType.COMMON
        )

        val foundMember = MemberDto(
            id = 10L,
            email = "user@test.com",
            password = "encoded",
            name = "일반회원",
            loginType = LoginType.COMMON
        )

        whenever(memberValidator.validateAndGetMemberForCommonLogin(loginDto))
            .thenReturn(foundMember)

        val accessToken = "ACCESS_TOKEN"
        val refreshToken = "REFRESH_TOKEN"
        val expiry = LocalDateTime.now().plusDays(7)

        whenever(jwtTokenProvider.createAccessToken(10L, "일반회원"))
            .thenReturn(accessToken)
        whenever(jwtTokenProvider.createRefreshToken(10L, "일반회원"))
            .thenReturn(refreshToken)
        whenever(jwtTokenProvider.getRefreshTokenExpiryLocalDateTime())
            .thenReturn(expiry)

        // when
        val result = memberAuthUseCase.login(loginDto)

        // then
        assertEquals(10L, result.id)
        assertEquals(accessToken, result.accessToken)
        assertEquals(refreshToken, result.refreshToken)

        verify(memberValidator, times(1))
            .validateAndGetMemberForCommonLogin(loginDto)

        verify(refreshTokenService, times(1))
            .saveNewToken(10L, refreshToken, expiry)

        verify(valueOps, times(1)).set(
            eq("auth:refresh:$refreshToken"),
            eq("10"),
            any<Duration>()
        )

        // SNS 관련 메서드는 호출 안 됨
        verify(memberService, never()).findByLoginTypeAndSnsId(any(), any())
        verify(memberService, never()).addMember(any<Member>())
    }

    @Test
    fun `SNS 로그인 - 기존 회원이면 조회만 하고 토큰만 발급한다`() {
        // given
        val snsLoginType = LoginType.KAKAO  // 실제 enum에 맞게 조정

        val requestDto = MemberDto(
            email = "sns@test.com",
            name = "SNS유저",
            loginType = snsLoginType,
            snsId = "SNS-1234"
        )

        val existing = MemberDto(
            id = 20L,
            email = "sns@test.com",
            name = "기존SNS유저",
            loginType = snsLoginType,
            snsId = "SNS-1234"
        )

        whenever(memberValidator.requireSnsId(requestDto))
            .thenReturn("SNS-1234")

        whenever(memberService.findByLoginTypeAndSnsId(snsLoginType, "SNS-1234"))
            .thenReturn(existing)

        val accessToken = "ACCESS_SNS"
        val refreshToken = "REFRESH_SNS"
        val expiry = LocalDateTime.now().plusDays(7)

        whenever(jwtTokenProvider.createAccessToken(20L, "기존SNS유저"))
            .thenReturn(accessToken)
        whenever(jwtTokenProvider.createRefreshToken(20L, "기존SNS유저"))
            .thenReturn(refreshToken)
        whenever(jwtTokenProvider.getRefreshTokenExpiryLocalDateTime())
            .thenReturn(expiry)

        // when
        val result = memberAuthUseCase.login(requestDto)

        // then
        assertEquals(20L, result.id)
        assertEquals(accessToken, result.accessToken)
        assertEquals(refreshToken, result.refreshToken)

        verify(memberValidator, times(1)).requireSnsId(requestDto)
        verify(memberService, times(1))
            .findByLoginTypeAndSnsId(snsLoginType, "SNS-1234")
        verify(memberService, never()).addMember(any<Member>())
        verify(memberSettingService, never())
            .createDefaultSetting(any())
    }

    @Test
    fun `SNS 로그인 - 신규 회원이면 자동 가입 후 기본 설정 생성하고 토큰을 발급한다`() {
        // given
        val snsLoginType = LoginType.KAKAO  // 실제 enum에 맞게 조정

        val requestDto = MemberDto(
            email = "new-sns@test.com",
            name = "신규SNS",
            loginType = snsLoginType,
            snsId = "SNS-5678"
        )

        val saved = MemberDto(
            id = 30L,
            email = "new-sns@test.com",
            name = "신규SNS",
            loginType = snsLoginType,
            snsId = "SNS-5678"
        )

        whenever(memberValidator.requireSnsId(requestDto))
            .thenReturn("SNS-5678")

        whenever(memberService.findByLoginTypeAndSnsId(snsLoginType, "SNS-5678"))
            .thenReturn(null)

        whenever(memberService.addMember(any<Member>()))
            .thenReturn(saved)

        whenever(memberSettingService.createDefaultSetting(any()))
            .thenReturn(MemberSettingDto().apply { memberId = 30L })

        val accessToken = "ACCESS_SNS_NEW"
        val refreshToken = "REFRESH_SNS_NEW"
        val expiry = LocalDateTime.now().plusDays(7)

        whenever(jwtTokenProvider.createAccessToken(30L, "신규SNS"))
            .thenReturn(accessToken)
        whenever(jwtTokenProvider.createRefreshToken(30L, "신규SNS"))
            .thenReturn(refreshToken)
        whenever(jwtTokenProvider.getRefreshTokenExpiryLocalDateTime())
            .thenReturn(expiry)

        // when
        val result = memberAuthUseCase.login(requestDto)

        // then
        assertEquals(30L, result.id)
        assertEquals(accessToken, result.accessToken)
        assertEquals(refreshToken, result.refreshToken)

        verify(memberValidator).requireSnsId(requestDto)
        verify(memberService).findByLoginTypeAndSnsId(snsLoginType, "SNS-5678")
        verify(memberService).addMember(any<Member>())
        verify(memberSettingService).createDefaultSetting(
            check {
                assertEquals(30L, it.memberId)
            }
        )
    }

    @Test
    fun `tokenLogin 성공 시 refreshToken 기반으로 새 토큰 세트를 발급한다`() {
        // given
        val oldRefreshToken = "OLD_REFRESH"
        val memberId = 1L
        val memberName = "토큰회원"

        // 0) Redis 세션이 살아있는 상황 흉내
        val redisKey = "auth:refresh:$oldRefreshToken"
        whenever(valueOps.get(redisKey)).thenReturn(memberId.toString())

        // 1) JWT 자체는 유효
        whenever(jwtTokenProvider.validateToken(oldRefreshToken))
            .thenReturn(true)

        // 2) DB에서 가져온 RefreshToken 엔티티 (mock 대신 진짜 객체 추천)
        val stored = RefreshToken(
            id = 10L,
            memberId = memberId,
            token = oldRefreshToken,
            expiresAt = LocalDateTime.now().plusDays(7),
            revoked = false
        )
        whenever(refreshTokenService.validateAndGet(oldRefreshToken))
            .thenReturn(stored)

        // 3) 토큰 안에서도 같은 memberId가 나온다고 가정
        whenever(jwtTokenProvider.getMemberIdFromToken(oldRefreshToken))
            .thenReturn(memberId)

        // 4) 회원 조회
        val member = Member(
            id = memberId,
            name = memberName,
            email = "token@test.com",
            password = "encoded",
            snsId = null,
            loginType = LoginType.COMMON,
            status = MemberStatus.ACTIVE
        )
        whenever(memberService.getFindMemberId(memberId))
            .thenReturn(Optional.of(member))

        // 5) 새 토큰 발급
        val newAccessToken = "NEW_ACCESS"
        val newRefreshToken = "NEW_REFRESH"
        val newExpiry = LocalDateTime.now().plusDays(7)

        whenever(jwtTokenProvider.createAccessToken(memberId, memberName))
            .thenReturn(newAccessToken)
        whenever(jwtTokenProvider.createRefreshToken(memberId, memberName))
            .thenReturn(newRefreshToken)
        whenever(jwtTokenProvider.getRefreshTokenExpiryLocalDateTime())
            .thenReturn(newExpiry)

        // when
        val result = memberAuthUseCase.tokenLogin(oldRefreshToken)

        // then
        assertEquals(memberId, result.id)
        assertEquals(newAccessToken, result.accessToken)
        assertEquals(newRefreshToken, result.refreshToken)

        verify(refreshTokenService).revokeToken(oldRefreshToken)
        verify(refreshTokenService).saveNewToken(memberId, newRefreshToken, newExpiry)
    }


    @Test
    fun `tokenLogin - 토큰이 유효하지 않으면 BusinessException INVALID_TOKEN 이 발생한다`() {
        // given
        val invalidRefreshToken = "INVALID"
        val memberId = 1L

        // 0) Redis 세션이 살아있는 상황 흉내
        val redisKey = "auth:refresh:$invalidRefreshToken"
        whenever(valueOps.get(redisKey)).thenReturn(memberId.toString())

        // 1) JWT 자체는 유효하지 않다고 설정
        whenever(jwtTokenProvider.validateToken(invalidRefreshToken))
            .thenReturn(false)

        // when & then
        val ex = assertThrows(BusinessException::class.java) {
            memberAuthUseCase.tokenLogin(invalidRefreshToken)
        }

        assertEquals(ErrorCode.INVALID_TOKEN, ex.errorCode)

        // 옵션: 이 분기에서는 DB는 아예 안 타는지 확인하고 싶다면
        verify(refreshTokenService, never()).validateAndGet(any())
    }


    @Test
    fun `logout - 이미 만료된 토큰이면 validateToken false여도 revokeToken 은 호출된다`() {
        // given
        val expiredRefreshToken = "EXPIRED"

        whenever(jwtTokenProvider.validateToken(expiredRefreshToken))
            .thenReturn(false)

        // when
        memberAuthUseCase.logout(expiredRefreshToken)

        // then
        verify(refreshTokenService, times(1)).revokeToken(expiredRefreshToken)
        verify(refreshTokenService, never()).validateAndGet(any())
    }


    @Test
    fun `refresh - reissueTokens 로부터 새 accessToken 만 반환한다`() {
        // given
        val oldRefreshToken = "OLD_REFRESH"
        val memberId = 1L
        val memberName = "토큰회원"

        // 0) Redis 세션이 살아있는 상황 흉내
        val redisKey = "auth:refresh:$oldRefreshToken"
        whenever(valueOps.get(redisKey)).thenReturn(memberId.toString())

        // 1) JWT 자체는 유효
        whenever(jwtTokenProvider.validateToken(oldRefreshToken))
            .thenReturn(true)

        // 2) ✅ DB에서 가져온 RefreshToken 엔티티를 실제 객체로 만들어서 memberId를 정확히 맞춰줌
        val stored = RefreshToken(
            id = 10L,
            memberId = memberId,
            token = oldRefreshToken,
            expiresAt = LocalDateTime.now().plusDays(7),
            revoked = false
        )
        whenever(refreshTokenService.validateAndGet(oldRefreshToken))
            .thenReturn(stored)

        // 3) 토큰 안에서도 같은 memberId가 나온다고 가정
        whenever(jwtTokenProvider.getMemberIdFromToken(oldRefreshToken))
            .thenReturn(memberId)

        // 4) 회원 조회
        val member = Member(
            id = memberId,
            name = memberName,
            email = "token@test.com",
            password = "encoded",
            snsId = null,
            loginType = LoginType.COMMON,
            status = MemberStatus.ACTIVE
        )
        whenever(memberService.getFindMemberId(memberId))
            .thenReturn(Optional.of(member))

        // 5) 새 토큰 발급
        val newAccessToken = "NEW_ACCESS"
        val newRefreshToken = "NEW_REFRESH"
        val newExpiry = LocalDateTime.now().plusDays(7)

        whenever(jwtTokenProvider.createAccessToken(memberId, memberName))
            .thenReturn(newAccessToken)
        whenever(jwtTokenProvider.createRefreshToken(memberId, memberName))
            .thenReturn(newRefreshToken)
        whenever(jwtTokenProvider.getRefreshTokenExpiryLocalDateTime())
            .thenReturn(newExpiry)

        // when
        val onlyAccess = memberAuthUseCase.refresh(oldRefreshToken)

        // then
        assertEquals(newAccessToken, onlyAccess)

        // reissueTokens 로직까지 같이 검증하고 싶다면:
        verify(refreshTokenService).revokeToken(oldRefreshToken)
        verify(refreshTokenService).saveNewToken(memberId, newRefreshToken, newExpiry)
    }


}
