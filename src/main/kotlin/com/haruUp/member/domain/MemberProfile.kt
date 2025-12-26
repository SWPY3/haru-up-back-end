package com.haruUp.member.domain

import com.haruUp.member.domain.dto.MemberProfileDto
import com.haruUp.member.domain.type.MemberGender
import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "member_profile")
class MemberProfile(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(nullable = false, unique = true)
    var memberId: Long = 0L,

    @Column(length = 50)
    var nickname: String? = null,

    var birthDt: LocalDateTime? = null,

    @Enumerated(EnumType.STRING)
    var gender: MemberGender? = null,

    @Column(length = 255)
    var imgId: Long? = null,

    @Column(length = 500)
    var intro: String? = null,

    var jobId: Long? = null,

    var jobDetailId: Long? = null
) {

    fun toDto(): MemberProfileDto =
        MemberProfileDto(
            id = id,
            memberId = memberId,
            nickname = nickname,
            imgId = imgId,
            intro = intro,
            birthDt = birthDt,
            gender = gender,
            jobId = jobId,
            jobDetailId = jobDetailId
        )
}