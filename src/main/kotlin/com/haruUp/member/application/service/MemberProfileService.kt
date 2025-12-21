package com.haruUp.member.application.service

import com.haruUp.category.application.JobDetailService
import com.haruUp.member.domain.MemberProfile
import com.haruUp.member.domain.dto.MemberProfileDto
import com.haruUp.member.infrastructure.MemberProfileRepository
import jakarta.transaction.Transactional
import org.hibernate.query.results.Builders.entity
import org.springframework.stereotype.Service
import kotlin.apply

@Service
class MemberProfileService (
    private val memberProfileRepository: MemberProfileRepository,
    private val jobDetailService: JobDetailService
) {

    @Transactional
    fun getByMemberId(memberId: Long): MemberProfileDto? =
        memberProfileRepository.findByMemberId(memberId)?.toDto()

    @Transactional
    fun createDefaultProfile(memberId: Long): MemberProfileDto {
        val entity = MemberProfile().apply { this.memberId = memberId  }
        return memberProfileRepository.save(entity).toDto()
    }

    @Transactional
    fun updateProfile(memberId: Long, profileDto: MemberProfileDto): MemberProfileDto {
        val existing = memberProfileRepository.findByMemberId(memberId)
            ?: MemberProfile().apply{ this.memberId = memberId}

        existing.nickname = profileDto.nickname
        existing.imgId = profileDto.imgId
        existing.intro = profileDto.intro

        return memberProfileRepository.save(existing).toDto()
    }

    @Transactional
    fun deleteByMemberId(memberId: Long) {
        memberProfileRepository.deleteByMemberId(memberId)
    }

    @Transactional
    fun curationUpdateProfile(memberId: Long, profileDto: MemberProfileDto) : MemberProfileDto {
        val existing = memberProfileRepository.findByMemberId(memberId)
            ?: MemberProfile(memberId = memberId)

        existing.nickname = profileDto.nickname
        existing.birthDt = profileDto.birthDt
        existing.gender = profileDto.gender

        return memberProfileRepository.save(existing).toDto()

    }

    @Transactional
    fun nicknameDuplicationCheck(nickname: String): Boolean {
        return memberProfileRepository.existsByNickname(nickname)
    }

    @Transactional
    fun memberJobUpdate(memberId: Long, jobId: Long): MemberProfileDto {
        val exising = getMemberProfile(memberId)

        exising.apply { this.jobId = jobId }

       return memberProfileRepository.save(exising).toDto()
    }

    @Transactional
    fun memberJobDetailUpdate(memberId: Long, jobDetailId: Long): MemberProfileDto {
        val exising = getMemberProfile(memberId)

        exising.apply { this.jobDetailId =   jobDetailId}

        return memberProfileRepository.save(exising).toDto()
    }

    @Transactional
    fun getMemberProfile(memberId : Long) : MemberProfile {
       return memberProfileRepository.findByMemberId(memberId)
            ?: MemberProfile(memberId = memberId)

    }
}