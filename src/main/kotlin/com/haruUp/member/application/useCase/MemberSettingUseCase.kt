package com.haruUp.member.application.useCase

import com.haruUp.auth.application.RefreshTokenService
import com.haruUp.global.error.BusinessException
import com.haruUp.global.error.ErrorCode
import com.haruUp.global.security.JwtTokenProvider
import com.haruUp.member.application.service.MemberProfileService
import com.haruUp.member.application.service.MemberService
import com.haruUp.member.application.service.MemberSettingService
import com.haruUp.member.application.service.MemberValidator
import com.haruUp.member.domain.dto.MemberSettingDto
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class MemberSettingUseCase (
    private val memberService: MemberService,
    private val memberSettingService: MemberSettingService,
) {

    // 내 세팅 정보 조회
    @Transactional(readOnly = true)
    fun getMySetting(memberId: Long): MemberSettingDto {
        val exists = memberService.getFindMemberId(memberId)
        if (exists.isEmpty) {
            throw BusinessException(ErrorCode.MEMBER_NOT_FOUND, "회원 정보를 찾을 수 없습니다.")
        }

        return memberSettingService.getByMemberId(memberId)
    }

    // 내 세팅 정보 수정
    @Transactional
    fun updateMySetting(memberId: Long, dto: MemberSettingDto): MemberSettingDto {
        val exists = memberService.getFindMemberId(memberId)
        if (exists.isEmpty) {
            throw BusinessException(ErrorCode.MEMBER_NOT_FOUND, "회원 정보를 찾을 수 없습니다.")
        }

        // 필요하면 dto 내의 값들에 대한 검증도 수행
        return memberSettingService.updateSettings(dto)
    }

}