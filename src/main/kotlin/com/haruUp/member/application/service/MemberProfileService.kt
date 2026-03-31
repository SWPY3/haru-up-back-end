package com.haruUp.member.application.service

import com.haruUp.category.application.JobDetailService
import com.haruUp.character.application.service.MemberCharacterService
import com.haruUp.member.domain.MemberProfile
import com.haruUp.member.domain.dto.MemberProfileDto
import com.haruUp.member.infrastructure.MemberProfileRepository
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service
import kotlin.apply

@Service
class MemberProfileService (
    private val memberProfileRepository: MemberProfileRepository,
    private val jobDetailService: JobDetailService,
    private val memberCharacterService: MemberCharacterService
) {

    /** 회원 프로필을 조회하고 선택된 캐릭터 ID를 함께 반환한다. */
    @Transactional
    fun getByMemberId(memberId: Long): MemberProfileDto? {
        val profileDto = memberProfileRepository.findByMemberId(memberId)?.toDto() ?: return null
        val memberCharacter = memberCharacterService.getSelectedCharacter(memberId)
        profileDto.characterId = memberCharacter?.characterId
        return profileDto
    }

    /** 기본 프로필을 생성하고 선택된 캐릭터 ID를 함께 반환한다. */
    @Transactional
    fun createDefaultProfile(memberId: Long): MemberProfileDto {
        val entity = MemberProfile().apply { this.memberId = memberId  }
        val profileDto = memberProfileRepository.save(entity).toDto()
        val memberCharacter = memberCharacterService.getSelectedCharacter(memberId)
        profileDto.characterId = memberCharacter?.characterId
        return profileDto
    }

    /** 회원 프로필 정보를 수정한다. */
    @Transactional
    fun updateProfile(memberId: Long, profileDto: MemberProfileDto): MemberProfileDto {
        val existing = memberProfileRepository.findByMemberId(memberId)
            ?: MemberProfile().apply { this.memberId = memberId }

        //직업이 자영업 세부직업 null 처리
        if(profileDto.jobId == 2L){
            existing.jobDetailId = null
            profileDto.jobDetailId = null
        }


        existing.nickname     = profileDto.nickname     ?: existing.nickname
        existing.birthDt      = profileDto.birthDt      ?: existing.birthDt
        existing.gender       = profileDto.gender       ?: existing.gender
        existing.imgId        = profileDto.imgId        ?: existing.imgId
        existing.intro        = profileDto.intro        ?: existing.intro
        existing.jobId        = profileDto.jobId        ?: existing.jobId
        existing.jobDetailId  = profileDto.jobDetailId  ?: existing.jobDetailId

        return memberProfileRepository.save(existing).toDto()
    }

    /** 회원 ID 기준으로 프로필을 삭제한다. */
    @Transactional
    fun deleteByMemberId(memberId: Long) {
        memberProfileRepository.deleteByMemberId(memberId)
    }

    /** 초기 큐레이션 과정에서 프로필 정보를 저장/갱신한다. */
    @Transactional
    fun curationUpdateProfile(memberId: Long, profileDto: MemberProfileDto) : MemberProfileDto {
        val existing = memberProfileRepository.findByMemberId(memberId)
            ?: MemberProfile(memberId = memberId)

        existing.nickname     = profileDto.nickname     ?: existing.nickname
        existing.birthDt      = profileDto.birthDt      ?: existing.birthDt
        existing.gender       = profileDto.gender       ?: existing.gender
        existing.imgId        = profileDto.imgId        ?: existing.imgId
        existing.intro        = profileDto.intro        ?: existing.intro
        existing.jobId        = profileDto.jobId        ?: existing.jobId
        existing.jobDetailId  = profileDto.jobDetailId  ?: existing.jobDetailId


        return memberProfileRepository.save(existing).toDto()

    }

    /** 닉네임 중복 여부를 조회한다. */
    @Transactional
    fun nicknameDuplicationCheck(nickname: String): Boolean {
        return memberProfileRepository.existsByNickname(nickname)
    }

    /** 회원의 직업 정보를 갱신한다. */
    @Transactional
    fun memberJobUpdate(memberId: Long, jobId: Long): MemberProfileDto {
        val exising = getMemberProfile(memberId)

        exising.apply { this.jobId = jobId }

       return memberProfileRepository.save(exising).toDto()
    }

    /** 회원의 세부 직업 정보를 갱신한다. */
    @Transactional
    fun memberJobDetailUpdate(memberId: Long, jobDetailId: Long): MemberProfileDto {
        val exising = getMemberProfile(memberId)

        exising.apply { this.jobDetailId =   jobDetailId}

        return memberProfileRepository.save(exising).toDto()
    }

    /** 회원 프로필 엔티티를 조회하고 없으면 기본 엔티티를 생성해 반환한다. */
    @Transactional
    fun getMemberProfile(memberId : Long) : MemberProfile {
       return memberProfileRepository.findByMemberId(memberId)
            ?: MemberProfile(memberId = memberId)

    }
}
