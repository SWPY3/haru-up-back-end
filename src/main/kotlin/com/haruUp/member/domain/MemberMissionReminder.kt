package com.haruUp.member.domain

import com.haruUp.global.common.BaseEntity
import com.haruUp.member.domain.dto.MemberMissionReminderDto
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import java.time.LocalDateTime

@Entity
class MemberMissionReminder(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long?,

    var memberId: Long,

    var reminderDt : LocalDateTime,

    ) : BaseEntity() {
   protected constructor() : this(null, 0, LocalDateTime.now())

    fun toDto() = MemberMissionReminderDto(
        id = this.id,
        memberId = this.memberId,
        reminderDt = this.reminderDt,
    )
}

