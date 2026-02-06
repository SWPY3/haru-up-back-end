package com.haruUp.notification.application.useCase

import com.haruUp.notification.application.PushClient
import com.haruUp.notification.application.service.NotificationTokenService
import com.haruUp.notification.infrastructure.PushClientApplication
import org.springframework.stereotype.Component

@Component
class NotificationUseCase(
    private val notificationTokenService: NotificationTokenService,
    private val pushClient: PushClient,
    private val pushClientApplication: PushClientApplication
) {
    /**
     * 단일 회원에게 푸시 발송
     */
    fun sendToMember(
        memberId: Long,
        title: String,
        body: String,
        data: Map<String, String> = emptyMap()
    ) {
        val tokens = notificationTokenService.getTokensByMember(memberId)

        tokens.forEach { token ->
            val message = pushClient.createMessage(
                token = token,
                title = title,
                body = body
            )

            pushClientApplication.send(token, message);
        }

    }



    /**
     * 단일 회원에게 푸시 발송
     */
    fun sendToMemberWithDeviceId(
        memberId: Long,
        deviceId : String,
        title: String,
        body: String,
    ) {

        val message = pushClient.createMessageWithDeviceid(
            deviceId = deviceId,
            title = title,
            body = body
        )


        pushClientApplication.sendWithDeviceId(deviceId, memberId, message);

    }

    /**
     * 여러 회원에게 동일한 내용 푸시 발송
     * (간단하게 memberId 루프 돌려서 재사용)
     */
    fun sendToMembers(
        memberIds: List<Long>,
        title: String,
        body: String,
        data: Map<String, String> = emptyMap()
    ) {
        memberIds.forEach { memberId ->
            sendToMember(memberId, title, body, data)
        }
    }
}