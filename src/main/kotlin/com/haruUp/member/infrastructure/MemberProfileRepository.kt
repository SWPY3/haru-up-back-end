package com.haruUp.member.infrastructure

import com.haruUp.member.domain.MemberProfile
import org.springframework.data.jpa.repository.JpaRepository

interface MemberProfileRepository : JpaRepository<MemberProfile, Long> {

    fun findByMemberId(memberId : Long) : MemberProfile?

    fun deleteByMemberId(memberId :Long)

    fun existsByNickname(nickname: String): Boolean
}