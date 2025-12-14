package com.haruUp.member.application.service

import com.haruUp.category.application.JobDetailService
import com.haruUp.member.domain.MemberProfile
import com.haruUp.member.domain.dto.MemberProfileDto
import com.haruUp.member.infrastructure.MemberProfileRepository
import org.hibernate.query.results.Builders.entity
import org.springframework.stereotype.Service
import kotlin.apply

@Service
class MemberProfileService (
    private val memberProfileRepository: MemberProfileRepository,
    private val jobDetailService: JobDetailService
) {

    fun getByMemberId(memberId: Long): MemberProfileDto? =
        memberProfileRepository.findByMemberId(memberId)?.toDto()

    fun createDefaultProfile(memberId: Long): MemberProfileDto {
        val entity = MemberProfile().apply { this.memberId = memberId  }
        return memberProfileRepository.save(entity).toDto()
    }

    fun updateProfile(memberId: Long, profileDto: MemberProfileDto): MemberProfileDto {
        val existing = memberProfileRepository.findByMemberId(memberId)
            ?: MemberProfile().apply{ this.memberId = memberId}

        existing.nickname = profileDto.nickname
        existing.imgId = profileDto.imgId
        existing.intro = profileDto.intro

        return memberProfileRepository.save(existing).toDto()
    }

    fun deleteByMemberId(memberId: Long) {
        memberProfileRepository.deleteByMemberId(memberId)
    }

    fun curationUpdateProfile(memberId: Long, profileDto: MemberProfileDto) : MemberProfileDto {
        val existing = memberProfileRepository.findByMemberId(memberId)
            ?: MemberProfile(memberId = memberId)

        existing.nickname = profileDto.nickname
        existing.birthDt = profileDto.birthDt
        existing.gender = profileDto.gender

        return memberProfileRepository.save(existing).toDto()

    }

    fun nicknameDuplicationCheck(nickname: String): Boolean {
        return memberProfileRepository.existsByNickname(nickname)
    }

    fun memberJobUpdate(memberId: Long, jobId: Long): MemberProfileDto {
        val exising = getMemberProfile(memberId)

        exising.apply { this.jobId = jobId }

       return memberProfileRepository.save(exising).toDto()
    }

    fun memberJobDetailUpdate(memberId: Long, jobDetailId: Long): MemberProfileDto {
        val exising = getMemberProfile(memberId)

        exising.apply { this.jobDetailId =   jobDetailId}

        return memberProfileRepository.save(exising).toDto()
    }

    fun getMemberProfile(memberId : Long) : MemberProfile {
       return memberProfileRepository.findByMemberId(memberId)
            ?: MemberProfile(memberId = memberId)

    }
}