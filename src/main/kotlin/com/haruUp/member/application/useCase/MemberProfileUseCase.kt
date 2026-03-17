package com.haruUp.member.application.useCase

import com.haruUp.character.application.CharacterUseCase
import com.haruUp.global.error.BusinessException
import com.haruUp.global.error.ErrorCode
import com.haruUp.member.application.service.MemberProfileService
import com.haruUp.member.application.service.MemberService
import com.haruUp.member.domain.dto.MemberProfileDto
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class MemberProfileUseCase(
    private val memberService: MemberService,
    private val memberProfileService: MemberProfileService,
    private val characterUseCase: CharacterUseCase
) {

    /** 내 프로필을 조회하고 없으면 기본 프로필을 생성해 반환한다. */
    @Transactional(readOnly = true)
    fun getMyProfile(memberId: Long): MemberProfileDto {
        validateMemberExists(memberId)

        return memberProfileService.getByMemberId(memberId)
            ?: memberProfileService.createDefaultProfile(memberId)
    }

    /**
     * ✏️ 내 프로필 수정
     */
    @Transactional
    fun updateMyProfile(memberId: Long, dto: MemberProfileDto): MemberProfileDto {
        validateMemberExists(memberId)

        // 여기서 nickname 길이, 금지어 등 검증을 Validator로 뺄 수도 있음
        return memberProfileService.updateProfile(memberId, dto)
    }


    /** 기본 프로필과 초기 캐릭터를 함께 생성한다. */
    @Transactional
    fun createDefaulProfile(memberId: Long?, characterId: Long) {
        val validMemberId = memberId
            ?: throw BusinessException(ErrorCode.MEMBER_NOT_FOUND, "회원의 memberId 찾을수 없습니다.")

        memberProfileService.createDefaultProfile(validMemberId)
        characterUseCase.createInitialCharacter(validMemberId, characterId)
    }

    /** 큐레이션 기반 프로필 정보를 저장한다. */
    fun addProfile(memberId: Long, memberProfileDto: MemberProfileDto): MemberProfileDto {
        return memberProfileService.curationUpdateProfile(memberId, memberProfileDto)
    }

    /** 닉네임 중복 여부를 조회한다. */
    fun nickNameDuplicationCheck(nickName: String): Boolean =
        memberProfileService.nicknameDuplicationCheck(nickName)

    /** 회원의 직업 정보를 갱신한다. */
    fun memberJobUpdate(memberId: Long, jobId: Long): MemberProfileDto {
        return memberProfileService.memberJobUpdate(memberId, jobId)
    }

    /** 회원의 직업 상세 정보를 갱신한다. */
    fun memberJobDetailUpdate(memberId: Long, jobDetailId: Long): MemberProfileDto {
        return memberProfileService.memberJobDetailUpdate(memberId, jobDetailId)
    }

    /** 회원 존재 여부를 검증한다. */
    private fun validateMemberExists(memberId: Long) {
        if (memberService.getFindMemberId(memberId).isEmpty) {
            throw BusinessException(ErrorCode.MEMBER_NOT_FOUND, "회원 정보를 찾을 수 없습니다.")
        }
    }
}
