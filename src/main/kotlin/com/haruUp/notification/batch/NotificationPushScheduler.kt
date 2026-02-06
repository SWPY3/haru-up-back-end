package com.haruUp.notification.batch

import com.haruUp.mission.application.MemberMissionUseCase
import com.haruUp.notification.application.useCase.NotificationUseCase
import jakarta.transaction.Transactional
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class NotificationPushScheduler(
    private val notificationUseCase: NotificationUseCase,
    private val memberMissionUseCase: MemberMissionUseCase
) {


    @Scheduled(cron =  "0 0 19 * * *")
    @Transactional
    fun sendDailyNotificationPush() {

       memberMissionUseCase.getMembersWithTodayFalseMission().forEach { target ->

           notificationUseCase.sendToMemberWithDeviceId(
               memberId = target.memberId,
               deviceId = target.deviceId,
               title = "오늘의 미션이 아직 완료되지 않았어요!",
               body = "하루업에서 오늘의 미션을 확인하고 캐릭터를 성장시켜보세요!"
           )
       }


    }

}