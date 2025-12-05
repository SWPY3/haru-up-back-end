package com.haruUp.member.application.useCase

import com.haruUp.auth.infrastructure.RefreshTokenRepository
import com.haruUp.global.security.JwtTokenProvider
import com.haruUp.member.domain.dto.MemberDto
import com.haruUp.member.domain.type.LoginType
import com.haruUp.member.infrastructure.MemberRepository
import com.haruUp.member.infrastructure.MemberSettingRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@SpringBootTest
@Transactional               // 각 테스트마다 롤백
class MemberAuthUseCaseIntegrationTest(

    @Autowired
    private val memberAuthUseCase: MemberAuthUseCase,

    @Autowired
    private val memberRepository: MemberRepository,

    @Autowired
    private val memberSettingRepository: MemberSettingRepository,

    @Autowired
    private val refreshTokenRepository: RefreshTokenRepository,

    @Autowired
    private var jwtTokenProvider: JwtTokenProvider,

    @Autowired
    private var stringRedisTemplate: StringRedisTemplate

) {


    @BeforeEach
    fun setUp() {
        // DB는 @Transactional 때문에 매 테스트마다 롤백됨
        memberRepository.deleteAll()
        memberSettingRepository.deleteAll()
        refreshTokenRepository.deleteAll()
    }

    // =========================================
    // 1) SNS 로그인 - 신규 회원 플로우
    // =========================================
    @Test
    fun `SNS 로그인 - 신규 회원이면 Member, Setting, RefreshToken 이 생성된다`() {
        // given
        val snsId = "KAKAO_123"
        val email = "kakao@test.com"
        val name = "카카오유저"

        val request = MemberDto(
            id = null,
            email = email,
            password = null,
            name = name,
            loginType = LoginType.KAKAO,
            snsId = snsId
        )

        // JWT 발급 부분은 실제 구현 대신 Stub
        whenever(jwtTokenProvider.createAccessToken(any(), any()))
            .thenReturn("ACCESS_TOKEN")
        whenever(jwtTokenProvider.createRefreshToken(any(), any()))
            .thenReturn("REFRESH_TOKEN")
        val refreshExpiry = LocalDateTime.now().plusDays(7)
        whenever(jwtTokenProvider.getRefreshTokenExpiryLocalDateTime())
            .thenReturn(refreshExpiry)

        // when
        val result = memberAuthUseCase.login(request)

        // then - 반환 DTO 검증
        assertNotNull(result.id)
        assertEquals(LoginType.KAKAO, result.loginType)
        assertEquals("ACCESS_TOKEN", result.accessToken)
        assertEquals("REFRESH_TOKEN", result.refreshToken)

        val savedMember = memberRepository
            .findByLoginTypeAndSnsIdAndDeletedFalse(LoginType.KAKAO, snsId)
        assertNotNull(savedMember)
        val memberId = requireNotNull(savedMember!!.id)

        // MemberSetting 이 생성되었는지
        val setting = memberSettingRepository.getByMemberId(memberId)
        assertNotNull(setting)

        // RefreshToken 이 DB에 저장되었는지
        val refreshEntity = refreshTokenRepository.findByToken("REFRESH_TOKEN")
        assertNotNull(refreshEntity)
        assertEquals(memberId, refreshEntity!!.memberId)
        assertFalse(refreshEntity.revoked)
    }

    // =========================================
    // 2) SNS 로그인 - 동일 snsId로 두 번 로그인해도 회원은 1명만 존재
    // =========================================
    @Test
    fun `SNS 로그인 - 기존 회원이 있으면 새로 생성하지 않고 동일 회원으로 로그인한다`() {
        // given
        val snsId = "KAKAO_DUP"
        val email = "dup@test.com"
        val name = "중복유저"

        val request = MemberDto(
            id = null,
            email = email,
            password = null,
            name = name,
            loginType = LoginType.KAKAO,
            snsId = snsId
        )

        whenever(jwtTokenProvider.createAccessToken(any(), any()))
            .thenReturn("ACCESS1", "ACCESS2")  // 첫 로그인, 두번째 로그인
        whenever(jwtTokenProvider.createRefreshToken(any(), any()))
            .thenReturn("REFRESH1", "REFRESH2")
        val refreshExpiry = LocalDateTime.now().plusDays(7)
        whenever(jwtTokenProvider.getRefreshTokenExpiryLocalDateTime())
            .thenReturn(refreshExpiry)

        // when 1) 첫 로그인 (자동 회원가입 + 기본 설정)
        val first = memberAuthUseCase.login(request)

        val memberCountAfterFirst = memberRepository.count()
        val settingCountAfterFirst = memberSettingRepository.count()

        // when 2) 같은 snsId로 다시 로그인
        val second = memberAuthUseCase.login(request)

        val memberCountAfterSecond = memberRepository.count()
        val settingCountAfterSecond = memberSettingRepository.count()

        // then
        assertEquals(memberCountAfterFirst, memberCountAfterSecond, "회원 수는 그대로여야 한다")
        assertEquals(1, memberCountAfterSecond)   // 실제로는 1명만 있어야 정상

        assertEquals(settingCountAfterFirst, settingCountAfterSecond, "기본 설정도 한 번만 생성되어야 한다")
        assertEquals(1, settingCountAfterSecond)

        // 두 번 로그인 결과가 같은 memberId 여야 함
        assertEquals(first.id, second.id)
    }

    // =========================================
    // 3) COMMON 회원가입 + COMMON 로그인 통합 플로우
    //    (signUp → login 까지 실제 DB/JPA 흐름 확인)
    // =========================================
    @Test
    fun `COMMON 회원가입 후 COMMON 로그인까지 전체 플로우가 동작한다`() {
        // given
        val rawPassword = "Password1!"
        val email = "common@test.com"
        val name = "일반회원"

        val signUpRequest = MemberDto(
            id = null,
            email = email,
            password = rawPassword,
            name = name,
            loginType = LoginType.COMMON,
            snsId = null
        )

        // signUp 내부에서 PasswordEncoder, Validator 등은 실제 Bean이 쓰임
        // 로그인 시에는 JWT + RefreshToken 저장이 필요하므로 여기서 미리 Stubbing
        whenever(jwtTokenProvider.createAccessToken(any(), any()))
            .thenReturn("ACCESS_COMMON")
        whenever(jwtTokenProvider.createRefreshToken(any(), any()))
            .thenReturn("REFRESH_COMMON")
        val refreshExpiry = LocalDateTime.now().plusDays(7)
        whenever(jwtTokenProvider.getRefreshTokenExpiryLocalDateTime())
            .thenReturn(refreshExpiry)

        // when 1) 회원가입
        val saved = memberAuthUseCase.signUp(signUpRequest)

        // then 1) DB에 회원 + 기본 설정이 실제로 존재
        val memberEntity = memberRepository.findById(saved.id!!).orElse(null)
        assertNotNull(memberEntity)
        val settingEntity = memberSettingRepository.getByMemberId(saved.id!!)
        assertNotNull(settingEntity)

        // when 2) COMMON 로그인
        val loginRequest = MemberDto(
            id = null,
            email = email,
            password = rawPassword,
            name = null,
            loginType = LoginType.COMMON,
            snsId = null
        )

        val loginResult = memberAuthUseCase.login(loginRequest)

        // then 2) 로그인 결과에 토큰이 세팅되고, 같은 회원으로 로그인됨
        assertEquals(saved.id, loginResult.id)
        assertEquals("ACCESS_COMMON", loginResult.accessToken)
        assertEquals("REFRESH_COMMON", loginResult.refreshToken)

        // RefreshToken 이 DB에 저장되었는지
        val refreshEntity = refreshTokenRepository.findByToken("REFRESH_COMMON")
        assertNotNull(refreshEntity)
        assertEquals(saved.id, refreshEntity!!.memberId)
    }
}
