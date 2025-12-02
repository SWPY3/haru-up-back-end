package com.haruUp.member.infrastructure

import com.haruUp.member.domain.MemberMissionReminder
import org.springframework.data.jpa.repository.JpaRepository

interface MemberMissionReminderRepository : JpaRepository<MemberMissionReminder, Long> {

    fun findAllByMemberIdAndDeletedFalse(memberId: Long) : List<MemberMissionReminder>
    fun findByIdAndDeletedFalse(memberId: Long): MemberMissionReminder?


}