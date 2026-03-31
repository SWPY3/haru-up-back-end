package com.haruUp.member.application.useCase

import com.haruUp.global.error.BusinessException
import com.haruUp.global.error.ErrorCode
import com.haruUp.member.application.service.MemberService
import com.haruUp.member.application.service.MemberSettingService
import com.haruUp.member.domain.dto.MemberSettingDto
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class MemberSettingUseCase(
    private val memberService: MemberService,
    private val memberSettingService: MemberSettingService,
) {

    /** 내 설정 정보를 조회한다. */
    @Transactional(readOnly = true)
    fun getMySetting(memberId: Long): MemberSettingDto {
        validateMemberExists(memberId)
        return memberSettingService.getByMemberId(memberId)
    }

    /** 내 설정 정보를 수정한다. */
    @Transactional
    fun updateMySetting(memberId: Long, dto: MemberSettingDto): MemberSettingDto {
        validateMemberExists(memberId)
        // 필요하면 dto 내의 값들에 대한 검증도 수행
        return memberSettingService.updateSettings(dto)
    }

    /** 회원 존재 여부를 검증한다. */
    private fun validateMemberExists(memberId: Long) {
        if (memberService.getFindMemberId(memberId).isEmpty) {
            throw BusinessException(ErrorCode.MEMBER_NOT_FOUND, "회원 정보를 찾을 수 없습니다.")
        }
    }
}
