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

    /** 내 리마인더 목록을 조회한다. */
    @Transactional(readOnly = true)
    fun getMyReminders(memberId: Long): List<MemberMissionReminderDto> {
        validateMemberExists(memberId)
        return memberRemindService.findByMemberId(memberId)
    }

    /** 새 리마인더를 생성한다. */
    @Transactional
    fun createReminder(memberId: Long, dto: MemberMissionReminderDto): MemberMissionReminderDto {
        validateMemberExists(memberId)

        val entity: MemberMissionReminder = dto.toEntity().apply {
            this.memberId = memberId   // 보안상 memberId는 서버에서 강제 세팅
        }

        val saved = memberRemindService.save(entity)
        return saved.toDto()
    }

    /** 기존 리마인더를 수정한다. */
    @Transactional
    fun updateReminder(memberId: Long, reminderId: Long, dto: MemberMissionReminderDto): MemberMissionReminderDto {
        validateMemberExists(memberId)

        val reminder = memberRemindService.findEntityById(reminderId)
            ?: throw BusinessException(ErrorCode.NOT_FOUND, "리마인더를 찾을 수 없습니다.")

        if (reminder.memberId != memberId) {
            throw BusinessException(ErrorCode.ACCESS_DENIED, "본인의 리마인더만 수정할 수 있습니다.")
        }

        reminder.apply {
            // 필요 필드들 계속 매핑
            this.memberId = memberId
            this.reminderDt = dto.reminderDt
        }

        val saved = memberRemindService.save(reminder)
        return saved.toDto()
    }

    /** 리마인더를 soft delete 처리한다. */
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

    /** 회원 존재 여부를 검증한다. */
    private fun validateMemberExists(memberId: Long) {
        if (memberService.getFindMemberId(memberId).isEmpty) {
            throw BusinessException(ErrorCode.MEMBER_NOT_FOUND, "회원 정보를 찾을 수 없습니다.")
        }
    }
}
