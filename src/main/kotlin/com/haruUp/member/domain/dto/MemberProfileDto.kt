package com.haruUp.member.domain.dto

import com.haruUp.member.domain.MemberProfile
import com.haruUp.member.domain.type.MemberGender
import jakarta.persistence.Column
import java.time.LocalDateTime

class MemberProfileDto (
    var id: Long? = null,

    // 물리 FK 안 쓰는 대신 memberId만 숫자로 저장
    @Column(nullable = false, unique = true)
    var memberId: Long,

    @Column(nullable = true, length = 50)
    var nickname: String? = null,

    var birthDt : LocalDateTime ?= null,

    var gender : MemberGender?= MemberGender.MALE,

    @Column(nullable = true, length = 255)
    var imgId: Long? = null,

    @Column(nullable = true, length = 500)
    var intro: String? = null,
) {

    protected constructor() : this(null, 0, null, null, null, null, null)

    fun toEntity() : MemberProfile =
        MemberProfile(
            id = this.id,
            memberId = this.memberId,
            nickname = this.nickname,
            imgId = this.imgId,
            intro = this.intro,
            birthDt = this.birthDt,
            gender = this.gender,
        )
}