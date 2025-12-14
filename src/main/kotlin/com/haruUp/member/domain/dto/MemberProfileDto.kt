package com.haruUp.member.domain.dto

import com.haruUp.member.domain.MemberProfile
import com.haruUp.member.domain.type.MemberGender
import java.time.LocalDateTime

data class MemberProfileDto(
    var id: Long? = null,

    // 물리 FK 안 쓰고 memberId 값만 전달
    var memberId: Long? = null,

    var nickname: String? = null,

    var birthDt: LocalDateTime? = null,

    var gender: MemberGender? = MemberGender.MALE,

    var imgId: Long? = null,

    var intro: String? = null,

    var jobId: Long? = null,

    var jobDetailId: Long? = null
) {

    fun toEntity(): MemberProfile =
        MemberProfile(
            id = id,
            memberId = memberId
                ?: throw IllegalArgumentException("memberId는 필수값입니다."),
            nickname = nickname,
            birthDt = birthDt,
            gender = gender,
            imgId = imgId,
            intro = intro,
            jobId = jobId,
            jobDetailId = jobDetailId
        )
}