package com.haruUp.member.domain.dto

import com.haruUp.member.domain.type.LoginType
import com.haruUp.member.domain.Member
import com.haruUp.member.domain.type.MemberStatus
import com.haruUp.member.domain.ValidationMessage
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.Pattern

class MemberDto(

    val id: Long? = null,

    val name: String? = "",

    @param:Pattern(regexp = "^[a-zA-Z0-9!@#$%^&*]{8,16}$", message = ValidationMessage.PASSWORD)
    var password: String? = "",

    @param:Email(message = ValidationMessage.EMAIL)
    var email: String? = "",

    val loginType: LoginType? = LoginType.COMMON,

    val snsId: String ?= "",

    val status : MemberStatus?= MemberStatus.ACTIVE,

    var accessToken : String ?= "",

    var refreshToken : String ?= ""

) {
    // JPA가 사용할 기본 생성자
    protected constructor() : this(null, "", "", "", null, "" , MemberStatus.ACTIVE , "", "")

    fun toEntity() : Member =
        Member(
            id = this.id,
            name = this.name,
            password = this.password,
            email = this.email,
            loginType = this.loginType,
            snsId = this.snsId,
            status = this.status
        )
}