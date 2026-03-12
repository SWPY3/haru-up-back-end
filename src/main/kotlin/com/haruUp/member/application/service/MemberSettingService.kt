package com.haruUp.member.application.service

import com.haruUp.member.domain.MemberSetting
import com.haruUp.member.domain.dto.MemberSettingDto
import com.haruUp.member.infrastructure.MemberSettingRepository
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service

@Service
class MemberSettingService (
    val memberSettingRepository : MemberSettingRepository
) {


    /** 기본 설정 정보를 생성한다. */
    @Transactional
    fun createDefaultSetting(memberSettingDto : MemberSettingDto) : MemberSettingDto {
        return  memberSettingRepository.save(memberSettingDto.toEntity()).toDto()
    }

    /** 회원 ID 기준으로 설정 정보를 조회한다. */
    @Transactional
    fun getByMemberId(memberId : Long ) : MemberSettingDto {
        return memberSettingRepository.getByMemberId(memberId).toDto()
    }

    /** 설정 정보를 저장/수정한다. */
    @Transactional
    fun updateSettings(memberSettingDto : MemberSettingDto) : MemberSettingDto {
        return memberSettingRepository.save(memberSettingDto.toEntity()).toDto()
    }

    /** memberId 기반으로 기본 설정을 생성한다. */
    @Transactional
    fun createDefaultFor(memberId: Long) : MemberSettingDto {
       return memberSettingRepository.save(MemberSettingDto().apply { this.memberId = memberId }.toEntity()).toDto()

    }

    /** 설정 정보를 soft delete 처리한다. */
    fun softDelete(memberSetting : MemberSetting ) {
        val saved = memberSetting.apply { this.deleted = true }
        memberSettingRepository.save(saved)
    }
}
