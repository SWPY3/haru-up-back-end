package com.haruUp.member.application.useCase

import com.haruUp.auth.application.RefreshTokenService
import com.haruUp.global.error.BusinessException
import com.haruUp.global.error.ErrorCode
import com.haruUp.global.security.JwtTokenProvider
import com.haruUp.member.application.service.MemberProfileService
import com.haruUp.member.application.service.MemberService
import com.haruUp.member.application.service.MemberSettingService
import com.haruUp.interest.service.MemberInterestService
import com.haruUp.mission.application.MemberMissionService
import com.haruUp.member.application.service.MemberValidator
import com.haruUp.member.domain.dto.MemberDto
import com.haruUp.member.domain.type.LoginType
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class MemberAccountUseCase(
    private val memberService: MemberService,
    private val memberSettingService: MemberSettingService,
    private val passwordEncoder: PasswordEncoder,
    private val memberValidator: MemberValidator,
    private val refreshTokenService: RefreshTokenService,
    private val memberInterestService: MemberInterestService,
    private val memberMissionService: MemberMissionService
){

    // 사용자 조회
    @Transactional(readOnly = true)
    fun findMemberById(memberId: Long): MemberDto {
        val member = memberService.getFindMemberId(memberId)
            .orElseThrow {
                BusinessException(ErrorCode.MEMBER_NOT_FOUND, "회원 정보를 찾을 수 없습니다.")
            }
        return member.toDto()
    }

    // 이메일 중복 검사
    @Transactional(readOnly = true)
    fun isEmailDuplicate(email: String): Boolean {
        val exists = memberService.findByEmailAndLoginType(email, LoginType.COMMON)
        return exists != null
    }

    // 이메일 변경
    @Transactional
    fun changeEmail(memberId: Long, newEmail: String) : MemberDto {
        // 1) 회원 조회
        val member = memberService.getFindMemberId(memberId)
            .orElseThrow {
                BusinessException(ErrorCode.MEMBER_NOT_FOUND, "회원 정보를 찾을 수 없습니다.")
            }

        // 2) 이메일 중복 검증
        memberValidator.validateEmailDuplication(newEmail)

        // 3) 이메일 변경
        member.email = newEmail
        return memberService.updateMember(member)
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

    // 회원 탈퇴
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

        memberInterestService.deleteMemberInterestsByMemberId(memberId)

        memberMissionService.deleteMemberMissionsByMemberId(memberId)
    }
}