package com.haruUp.member.domain

import com.haruUp.member.domain.dto.MemberProfileDto
import com.haruUp.member.domain.type.MemberGender
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import org.hibernate.dialect.GroupBySummarizationRenderingStrategy
import java.time.LocalDateTime

@Entity
class MemberProfile (

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(nullable = false, unique = true)
    var memberId: Long,

    @Column(nullable = true, length = 50)
    var nickname: String? = null,

    @Column(nullable = true)
    var birthDt : LocalDateTime ?= null,

    var gender : MemberGender?= MemberGender.MALE,

    @Column(nullable = true, length = 255)
    var imgId: Long? = null,

    @Column(nullable = true, length = 500)
    var intro: String? = null,
) {

    protected constructor() : this(null, 0, null, null, null, null, null)

    fun toDto() : MemberProfileDto =
        MemberProfileDto(
            id = this.id,
            memberId = this.memberId,
            nickname = this.nickname,
            imgId = this.imgId,
            intro = this.intro,
            birthDt = this.birthDt,
            gender = this.gender
        )
}