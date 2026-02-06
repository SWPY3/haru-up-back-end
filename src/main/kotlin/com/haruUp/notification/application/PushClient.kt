package com.haruUp.notification.application

import com.google.firebase.messaging.Message
import com.google.firebase.messaging.Notification
import com.haruUp.notification.domain.NotificationDeviceToken
import org.springframework.stereotype.Component

@Component
class PushClient {

    fun createMessage(
        token: NotificationDeviceToken,
        title: String,
        body: String
    ): Message =
        Message.builder()
            .setToken(token.token)
            .setNotification(
                Notification.builder()
                    .setTitle(title)
                    .setBody(body)
                    .build()
            )
            .build()


    fun createMessageWithDeviceid(
        deviceId: String,
        title: String,
        body: String
    ): Message =
        Message.builder()
            .setToken(deviceId)
            .setNotification(
                Notification.builder()
                    .setTitle(title)
                    .setBody(body)
                    .build()
            )
            .build()
}