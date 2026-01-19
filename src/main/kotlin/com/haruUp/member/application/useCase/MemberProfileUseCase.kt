package com.haruUp.member.application.useCase

import com.haruUp.auth.application.RefreshTokenService
import com.haruUp.character.application.CharacterUseCase
import com.haruUp.global.error.BusinessException
import com.haruUp.global.error.ErrorCode
import com.haruUp.global.security.JwtTokenProvider
import com.haruUp.member.application.service.MemberProfileService
import com.haruUp.member.application.service.MemberService
import com.haruUp.member.application.service.MemberSettingService
import com.haruUp.member.application.service.MemberValidator
import com.haruUp.member.domain.dto.MemberProfileDto
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class MemberProfileUseCase (
    private val memberService: MemberService,
    private val memberProfileService: MemberProfileService,
    private val characterUseCase : CharacterUseCase
) {

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


    @Transactional
    fun createDefaulProfile(memberId: Long?, characterId: Long) {

        if(memberId  == null) throw BusinessException(ErrorCode.MEMBER_NOT_FOUND , "회원의 memberId 찾을수 없습니다.")

        memberProfileService.createDefaultProfile(memberId)
        characterUseCase.createInitialCharacter(memberId, characterId);

    }

    fun addProfile(memberId : Long, memberProfileDto: MemberProfileDto) : MemberProfileDto {
        return memberProfileService.curationUpdateProfile(memberId, memberProfileDto)
    }

    fun nickNameDuplicationCheck(nickName : String) : Boolean {
       return memberProfileService.nicknameDuplicationCheck(nickName )
    }

    fun memberJobUpdate(memberId: Long, jobId: Long) : MemberProfileDto {
        return memberProfileService.memberJobUpdate(memberId, jobId)

    }

    fun memberJobDetailUpdate(memberId : Long , jobDetailId : Long) : MemberProfileDto{
        return memberProfileService.memberJobDetailUpdate(memberId, jobDetailId)
    }

}