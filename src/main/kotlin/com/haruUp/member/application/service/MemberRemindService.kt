package com.haruUp.member.application.service

import com.haruUp.member.domain.MemberMissionReminder
import com.haruUp.member.domain.dto.MemberMissionReminderDto
import com.haruUp.member.infrastructure.MemberMissionReminderRepository
import org.springframework.stereotype.Service

@Service
class MemberRemindService(
    private val memberMissionReminderRepository : MemberMissionReminderRepository
) {

    /** 회원 미션 리마인더를 저장/수정한다. */
    fun save(memberMissionReminder: MemberMissionReminder): MemberMissionReminder {
        return memberMissionReminderRepository.save(memberMissionReminder)
    }

    /** 회원의 활성 리마인더 목록을 조회한다. */
    fun findByMemberId(memberId: Long): List<MemberMissionReminderDto> {
        return memberMissionReminderRepository
            .findAllByMemberIdAndDeletedFalse(memberId)
            .map { it.toDto() }
    }

    /** 리마인더 엔티티를 ID 기준으로 조회한다. */
    fun findEntityById(id: Long): MemberMissionReminder? =
        memberMissionReminderRepository.findByIdAndDeletedFalse(id)

    /** 미션 리마인더 알림 발송 로직의 확장 포인트다. */
    fun memberMissionReminderSendNotification() {
        // TODO: 차후 NotificationUseCase 붙여서 개발
    }

    /** 리마인더 스케줄링 로직의 확장 포인트다. */
    fun memberMissionReminderScheduling() {
        // TODO: @Scheduled로 호출할 컴포넌트 따로 빼는게 보통 패턴
    }

}
