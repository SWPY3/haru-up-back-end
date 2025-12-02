package com.haruUp.member.application.useCase

import com.haruUp.global.error.BusinessException
import com.haruUp.global.error.ErrorCode
import com.haruUp.member.application.service.MemberRemindService
import com.haruUp.member.application.service.MemberService
import com.haruUp.member.domain.MemberMissionReminder
import com.haruUp.member.domain.dto.MemberMissionReminderDto
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class MemberReminderUseCase(
    private val memberService: MemberService,
    private val memberRemindService: MemberRemindService,
) {

    @Transactional(readOnly = true)
    fun getMyReminders(memberId: Long): List<MemberMissionReminderDto> {
        // 회원 존재 여부 검증
        val exists = memberService.getFindMemberId(memberId)
        if (exists.isEmpty) {
            throw BusinessException(ErrorCode.MEMBER_NOT_FOUND, "회원 정보를 찾을 수 없습니다.")
        }

        return memberRemindService.findByMemberId(memberId)
    }

    @Transactional
    fun createReminder(memberId: Long, dto: MemberMissionReminderDto): MemberMissionReminderDto {
        val exists = memberService.getFindMemberId(memberId)
        if (exists.isEmpty) {
            throw BusinessException(ErrorCode.MEMBER_NOT_FOUND, "회원 정보를 찾을 수 없습니다.")
        }

        val entity: MemberMissionReminder = dto.toEntity().apply {
            this.memberId = memberId   // 보안상 memberId는 서버에서 강제 세팅
        }

        val saved = memberRemindService.save(entity)
        return saved.toDto()
    }

    @Transactional
    fun updateReminder(memberId: Long, reminderId: Long, dto: MemberMissionReminderDto): MemberMissionReminderDto {
        val exists = memberService.getFindMemberId(memberId)
        if (exists.isEmpty) {
            throw BusinessException(ErrorCode.MEMBER_NOT_FOUND, "회원 정보를 찾을 수 없습니다.")
        }

        val reminder = memberRemindService.findEntityById(reminderId)
            ?: throw BusinessException(ErrorCode.NOT_FOUND, "리마인더를 찾을 수 없습니다.")

        if (reminder.memberId != memberId) {
            throw BusinessException(ErrorCode.ACCESS_DENIED, "본인의 리마인더만 수정할 수 있습니다.")
        }

        reminder.apply {
            // 필요 필드들 계속 매핑
            this.memberId = dto.memberId
            this.reminderDt = dto.reminderDt
        }

        val saved = memberRemindService.save(reminder)
        return saved.toDto()
    }

    @Transactional
    fun deleteReminder(memberId: Long, reminderId: Long) {
        val reminder = memberRemindService.findEntityById(reminderId)
            ?: return

        if (reminder.memberId != memberId) {
            throw BusinessException(ErrorCode.ACCESS_DENIED, "본인의 리마인더만 삭제할 수 있습니다.")
        }

        reminder.deleted = true
        memberRemindService.save(reminder)
    }
}