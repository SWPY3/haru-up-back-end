package com.haruUp.member.application.service

import com.haruUp.member.domain.MemberMissionReminder
import com.haruUp.member.domain.dto.MemberMissionReminderDto
import com.haruUp.member.infrastructure.MemberMissionReminderRepository
import org.springframework.stereotype.Service

@Service
class MemberRemindService(
    private val memberMissionReminderRepository : MemberMissionReminderRepository
) {

    // 1. 회원 미션 리마인더 설정 저장/수정 (JPA save는 둘 다 처리)
    fun save(memberMissionReminder: MemberMissionReminder): MemberMissionReminder {
        return memberMissionReminderRepository.save(memberMissionReminder)
    }

    // 2. 회원 미션 리마인더 설정 조회 ( List )
    fun findByMemberId(memberId: Long): List<MemberMissionReminderDto> {
        return memberMissionReminderRepository
            .findAllByMemberIdAndDeletedFalse(memberId)
            .map { it.toDto() }
    }

    // 필요하면 엔티티 단위 조회도
    fun findEntityById(id: Long): MemberMissionReminder? =
        memberMissionReminderRepository.findByIdAndDeletedFalse(id)

    // 4. 미션 리마인더 알림 발송
    fun memberMissionReminderSendNotification() {
        // TODO: 차후 NotificationUseCase 붙여서 개발
    }

    // 5. 리마인더 알림 스케줄링 관리
    fun memberMissionReminderScheduling() {
        // TODO: @Scheduled로 호출할 컴포넌트 따로 빼는게 보통 패턴
    }

}