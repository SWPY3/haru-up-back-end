package com.haruUp.member.application.useCase

import com.haruUp.auth.application.RefreshTokenService
import com.haruUp.global.error.BusinessException
import com.haruUp.global.error.ErrorCode
import com.haruUp.global.security.JwtTokenProvider
import com.haruUp.member.application.service.MemberProfileService
import com.haruUp.member.application.service.MemberService
import com.haruUp.member.application.service.MemberSettingService
import com.haruUp.member.application.service.MemberValidator
import com.haruUp.member.domain.Member.LoginType
import com.haruUp.member.domain.Member.Member
import com.haruUp.member.domain.Member.MemberDto
import com.haruUp.member.domain.MemberSetting.MemberSettingDto
import com.haruUp.member.domain.profile.MemberProfileDto
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class MemberUseCase(
    private val memberService: MemberService,
    private val memberSettingService: MemberSettingService,
    private val memberProfileService: MemberProfileService,
    private val jwtTokenProvider: JwtTokenProvider,
    private val passwordEncoder: PasswordEncoder,
    private val memberValidator: MemberValidator,
    private val refreshTokenService: RefreshTokenService,
) {

    /**
     * COMMON 회원가입
     * 1) 입력값/중복 검증
     * 2) 비밀번호 암호화
     * 3) 회원 저장 (COMMON 타입으로)
     * 4) 기본 설정(MemberSetting) 생성
     */
    @Transactional
    fun signUp(memberDto: MemberDto): MemberDto {
        // 1) COMMON 회원가입 검증 (이메일, 비번, 중복 등)
        memberValidator.validateCommonSignUp(memberDto)

        // 2) 비밀번호 암호화
        val encodedPassword = passwordEncoder.encode(memberDto.password!!)

        // 3) DTO → 엔티티 변환 + 암호화된 비번, 로그인타입 세팅
        val entity = memberDto.toEntity().apply {
            this.password = encodedPassword
            this.loginType = LoginType.COMMON
        }

        // 4) 회원 저장 (Service는 DTO 반환)
        val savedMemberDto: MemberDto = memberService.addMember(entity)

        // 5) 기본 설정 생성
        memberSettingService.createDefaultSetting(
            MemberSettingDto().apply { memberId = requireNotNull(savedMemberDto.id) }
        )

        return savedMemberDto
    }

    /**
     *   로그인 (COMMON + SNS)
     * - COMMON : 이메일/비번 검증 후 토큰 발급
     * - SNS    : snsId 기준으로 찾고 없으면 자동 가입 후 토큰 발급
     * - 공통 : accessToken + refreshToken 발급 및 refreshToken 저장
     */
    @Transactional
    fun login(requestDto: MemberDto): MemberDto {

        // 1) 로그인 타입에 따라 회원 조회/생성
        val memberDto: MemberDto = when (requestDto.loginType) {
            LoginType.COMMON -> {
                // 이메일/비밀번호 검증 + 회원 조회까지 Validator가 처리하고 DTO 반환
                memberValidator.validateAndGetMemberForCommonLogin(requestDto)
            }

            else -> {
                // SNS 로그인
                val snsId = memberValidator.requireSnsId(requestDto)

                var found = memberService.findByLoginTypeAndSnsId(requestDto.loginType, snsId)

                // 없으면 자동 가입 + 기본 설정 생성
                if (found == null) {
                    val name = requestDto.name ?: requestDto.email ?: "사용자"

                    found = memberService.addMember(
                        Member().apply {
                            this.snsId = snsId
                            this.name = name
                            this.loginType = requestDto.loginType
                            this.email = requestDto.email
                        }
                    )

                    memberSettingService.createDefaultSetting(
                        MemberSettingDto().apply { memberId = requireNotNull(found.id) }
                    )
                }

                found
            }
        } ?: throw IllegalStateException("로그인 과정에서 memberDto가 null입니다.")

        // 2) 토큰 발급에 필요한 정보 추출
        val memberId = requireNotNull(memberDto.id) { "member.id가 없습니다." }
        val memberName = requireNotNull(memberDto.name) { "member.name이 없습니다." }

        // 3) accessToken + refreshToken 발급
        val accessToken = jwtTokenProvider.createAccessToken(memberId, memberName)
        val refreshToken = jwtTokenProvider.createRefreshToken(memberId, memberName)

        // 4) refreshToken 저장 (기존 것들 정리하는 정책은 RefreshTokenService 내에서 처리)
        val refreshExpiry = jwtTokenProvider.getRefreshTokenExpiryLocalDateTime()
        refreshTokenService.saveNewToken(memberId, refreshToken, refreshExpiry)

        // 5) 토큰 세팅해서 반환
        return memberDto.apply {
            this.accessToken = accessToken
            this.refreshToken = refreshToken
        }
    }

    /**
     * refreshToken만으로 로그인 (앱 재실행 시 자동 로그인용)
     *
     * - 클라이언트는 refreshToken 하나만 보내면
     *   → 서버가 토큰 검증 + 회원 조회 + 새 access/refresh 발급까지 수행
     */
    @Transactional
    fun tokenLogin(refreshToken: String): MemberDto {
        // 내부적으로는 refresh() 와 동일한 동작을 하도록 추상화
        return reissueTokens(refreshToken)
    }

    /**
     *  로그아웃
     * - 전달받은 refreshToken 을 폐기(revoke)
     * - accessToken 은 짧게 가져가고 별도 블랙리스트는 사용하지 않는 정책
     */
    @Transactional
    fun logout(refreshToken: String) {
        // 1) 토큰이 유효하지 않더라도(DB에는 남아있을 수 있으므로) revoke는 시도
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            // 이미 만료되었더라도 DB의 토큰은 폐기
            refreshTokenService.revokeToken(refreshToken)
            return
        }

        // 2) 토큰에서 memberId 추출
        val memberIdFromToken = jwtTokenProvider.getMemberIdFromToken(refreshToken)

        // 3) DB에 있는 토큰 검증 (존재, 소유자 일치, 만료/폐기 여부)
        val stored = refreshTokenService.validateAndGet(refreshToken)
        if (stored.memberId != memberIdFromToken) {
            throw BusinessException(ErrorCode.INVALID_TOKEN, "토큰 소유자가 일치하지 않습니다.")
        }

        // 4) 폐기
        refreshTokenService.revokeToken(refreshToken)
    }

    /**
     *  토큰 재발급 전용 (Refresh API)
     * - refreshToken의 유효성을 확인하고
     * - 새 accessToken + refreshToken 세트를 발급 후 반환
     *
     * tokenLogin() 과 로직은 동일하고, API 레벨에서 의미만 다르게 가져갈 수 있음.
     */
    @Transactional
    fun refresh(refreshToken: String): MemberDto {
        return reissueTokens(refreshToken)
    }

    /**
     * - refreshToken 기준으로
     *   1) JWT 유효성 검사
     *   2) DB 상태 검사 (존재, revoked, 만료)
     *   3) 회원 조회
     *   4) 새 accessToken + refreshToken 발급
     *   5) 기존 refreshToken 폐기 + 새 refreshToken 저장
     *   6) MemberDto + 새 토큰 세트 반환
     *
     * tokenLogin() / refresh() 에서 공통으로 사용
     */
    private fun reissueTokens(refreshToken: String): MemberDto {
        // 1) JWT 자체 유효성 검사 (서명 + 만료)
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw BusinessException(ErrorCode.INVALID_TOKEN, "유효하지 않은 리프레시 토큰입니다.")
        }

        // 2) DB 상태 검사 (존재, revoked, 만료)
        val stored = refreshTokenService.validateAndGet(refreshToken)

        val memberIdFromToken = jwtTokenProvider.getMemberIdFromToken(refreshToken)
        if (stored.memberId != memberIdFromToken) {
            throw BusinessException(ErrorCode.INVALID_TOKEN, "리프레시 토큰의 회원 정보가 일치하지 않습니다.")
        }

        // 3) 회원 조회 (Optional<Member> 라고 가정)
        val memberOpt = memberService.getFindMemberId(memberIdFromToken)
        if (memberOpt.isEmpty) {
            throw BusinessException(ErrorCode.MEMBER_NOT_FOUND, "회원 정보를 찾을 수 없습니다.")
        }
        val member: Member = memberOpt.get()

        val memberName = member.name ?: throw BusinessException(
            ErrorCode.MEMBER_NOT_FOUND_NAME,
            "회원 이름이 없습니다."
        )

        // 4) 새 accessToken + refreshToken 발급
        val newAccessToken = jwtTokenProvider.createAccessToken(memberIdFromToken, memberName)
        val newRefreshToken = jwtTokenProvider.createRefreshToken(memberIdFromToken, memberName)
        val newRefreshExpiry = jwtTokenProvider.getRefreshTokenExpiryLocalDateTime()

        // 5) 기존 refreshToken 폐기 + 새 refreshToken 저장
        refreshTokenService.revokeToken(refreshToken)
        refreshTokenService.saveNewToken(memberIdFromToken, newRefreshToken, newRefreshExpiry)

        // 6) 유저 정보 + 새 토큰 세트 반환
        return member.toDto().apply {
            this.accessToken = newAccessToken
            this.refreshToken = newRefreshToken
        }
    }

    /**
     * 🔑 비밀번호 변경 (COMMON 계정만)
     * - 기존 비밀번호 검증 후 새 비밀번호로 교체
     */
    @Transactional
    fun changePassword(memberId: Long, currentPassword: String, newPassword: String) {
        // 1) 회원 조회
        val member = memberService.getFindMemberId(memberId)
            .orElseThrow {
                BusinessException(ErrorCode.MEMBER_NOT_FOUND, "회원 정보를 찾을 수 없습니다.")
            }

        // 2) COMMON 계정만 비밀번호 변경 허용
        if (member.loginType != LoginType.COMMON) {
            throw BusinessException(
                ErrorCode.INVALID_INPUT,
                "SNS 로그인 계정은 비밀번호를 변경할 수 없습니다."
            )
        }

        // 3) 기존 비밀번호 검증
        val encoded = member.password
            ?: throw BusinessException(ErrorCode.INVALID_STATE, "저장된 비밀번호가 없습니다.")

        if (!passwordEncoder.matches(currentPassword, encoded)) {
            throw BusinessException(ErrorCode.INVALID_CREDENTIALS, "기존 비밀번호가 일치하지 않습니다.")
        }

        // 4) 새 비밀번호 검증 (간단 버전 – 필요하면 Validator로 분리)
        if (newPassword.length < 8) {
            throw BusinessException(ErrorCode.INVALID_INPUT, "새 비밀번호는 8자리 이상이어야 합니다.")
        }

        // 5) 새 비밀번호 저장
        val newEncoded = passwordEncoder.encode(newPassword)
        member.password = newEncoded

        memberService.updateMember(member)  // 반환값은 굳이 안 써도 됨
    }

    @Transactional
    fun withdraw(memberId: Long, passwordForCheck: String?) {
        // 1) 회원 조회
        val member = memberService.getFindMemberId(memberId)
            .orElseThrow {
                BusinessException(ErrorCode.MEMBER_NOT_FOUND, "회원 정보를 찾을 수 없습니다.")
            }

        // 2) COMMON 계정은 비밀번호 검증
        if (member.loginType == LoginType.COMMON) {
            val raw = passwordForCheck
                ?: throw BusinessException(ErrorCode.INVALID_INPUT, "비밀번호가 필요합니다.")

            val encoded = member.password
                ?: throw BusinessException(ErrorCode.INVALID_STATE, "저장된 비밀번호가 없습니다.")

            if (!passwordEncoder.matches(raw, encoded)) {
                throw BusinessException(ErrorCode.INVALID_CREDENTIALS, "비밀번호가 일치하지 않습니다.")
            }
        }

        val id = requireNotNull(member.id) { "member.id 가 없습니다." }

        // 3) RefreshToken은 어차피 수명 짧고, 보안상 확실히 제거하는 게 좋아서 hard delete 유지
        refreshTokenService.deleteAllByMemberId(memberId)

        // 4) MemberSetting soft delete
        val byMemberId = memberSettingService.getByMemberId(memberId)
        memberSettingService.softDelete(byMemberId.toEntity())

        // 5) Member soft delete
        memberService.softDelete(member)
    }



    @Transactional(readOnly = true)
    fun getMyProfile(memberId: Long): MemberProfileDto {
        // 회원 존재 여부 먼저 확인해도 좋음
        val exists = memberService.getFindMemberId(memberId)
        if (exists.isEmpty) {
            throw BusinessException(ErrorCode.MEMBER_NOT_FOUND, "회원 정보를 찾을 수 없습니다.")
        }

        return memberProfileService.getByMemberId(memberId)
            ?: memberProfileService.createDefaultProfile(memberId)
    }

    /**
     * ✏️ 내 프로필 수정
     */
    @Transactional
    fun updateMyProfile(memberId: Long, dto: MemberProfileDto): MemberProfileDto {
        val exists = memberService.getFindMemberId(memberId)
        if (exists.isEmpty) {
            throw BusinessException(ErrorCode.MEMBER_NOT_FOUND, "회원 정보를 찾을 수 없습니다.")
        }

        // 여기서 nickname 길이, 금지어 등 검증을 Validator로 뺄 수도 있음
        return memberProfileService.updateProfile(memberId, dto)
    }
}