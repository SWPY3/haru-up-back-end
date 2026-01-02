package com.haruUp.member.domain.dto

import com.haruUp.global.common.BaseEntity
import com.haruUp.member.domain.type.LoginType
import com.haruUp.member.domain.Member
import com.haruUp.member.domain.type.MemberStatus
import com.haruUp.member.domain.ValidationMessage
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.Pattern
import lombok.AccessLevel
import lombok.AllArgsConstructor
import lombok.Builder
import lombok.NoArgsConstructor

@NoArgsConstructor(access = AccessLevel.PROTECTED)   // JPA용
@AllArgsConstructor                                  // Builder용
@Builder                                             // 체이닝용
class MemberDto(

    var id: Long? = null,

    var name: String? = "",

    @param:Pattern(regexp = "^[a-zA-Z0-9!@#$%^&*]{8,16}$", message = ValidationMessage.PASSWORD)
    var password: String? = "",

    @param:Email(message = ValidationMessage.EMAIL)
    var email: String? = "",

    var loginType: LoginType? = LoginType.COMMON,

    var snsId: String ?= "",

    var status : MemberStatus?= MemberStatus.ACTIVE,

    var accessToken : String ?= "",

    var refreshToken : String ?= "",

) : BaseEntity() {

    fun toEntity(): Member =

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