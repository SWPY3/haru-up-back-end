package com.haruUp.member.application.service

import com.haruUp.member.domain.MemberMissionReminder
import com.haruUp.member.domain.dto.MemberMissionReminderDto
import com.haruUp.member.infrastructure.MemberMissionReminderRepository
import org.springframework.stereotype.Service

@Service
class MemberRemindService(
    private val memberMissionReminderRepository : MemberMissionReminderRepository
) {

    /*
    * 1. 회읜 미션 리마인더 설정 저장
    *
    * 2. 회원 미션 리마인더 설정 조회
    *
    * 3. 회원 미션 리마인더 설정 수정
    *
    * 4. 미션 리마인더 알림 발송
    *
    * 5. 리마인더 알림 스케줄링 관리
    *
    * */

    // 1. 회읜 미션 리마인더 설정 저장
    fun saveMemberMissionReminderSetting(memberMissionReminder : MemberMissionReminder) {
        memberMissionReminderRepository.save(memberMissionReminder)
    }

    // 2. 회원 미션 리마인더 설정 조회 ( List )
    fun getMemberMissionReminderSettingByMemberId(memberId : Long) : List<MemberMissionReminderDto>? {
        return memberMissionReminderRepository.findAllByIdAndDeletedFalse(memberId).stream()
            .map { reminder ->  reminder.toDto()}
            .toList()
    }

    fun updateMemberMissionReminderSetting(memberMissionReminderDto: MemberMissionReminderDto) {

    }

}