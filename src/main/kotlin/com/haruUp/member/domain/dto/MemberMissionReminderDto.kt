package com.haruUp.member.domain.dto

import com.haruUp.global.common.BaseEntity
import com.haruUp.member.domain.MemberMissionReminder
import java.time.LocalDateTime

class MemberMissionReminderDto(

    val id: Long?,

    val memberId: Long,

    val reminderDt : LocalDateTime,

) : BaseEntity(){

    protected constructor() : this(null, 0, LocalDateTime.now())

    fun toEntity() = MemberMissionReminder(
        id = this.id,
        memberId = this.memberId,
        reminderDt = this.reminderDt,
    )
}
