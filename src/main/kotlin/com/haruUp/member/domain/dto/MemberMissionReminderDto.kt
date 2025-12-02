package com.haruUp.member.domain.dto

import com.haruUp.global.common.BaseEntity
import com.haruUp.member.domain.MemberMissionReminder
import java.time.LocalDateTime

class MemberMissionReminderDto(

    var id: Long?,

    var memberId: Long,

    var reminderDt : LocalDateTime,

) : BaseEntity(){

    protected constructor() : this(null, 0, LocalDateTime.now())

    fun toEntity() = MemberMissionReminder(
        id = this.id,
        memberId = this.memberId,
        reminderDt = this.reminderDt,
    )
}
