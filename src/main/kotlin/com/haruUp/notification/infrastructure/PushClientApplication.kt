package com.haruUp.notification.infrastructure

import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingException
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.MessagingErrorCode
import com.haruUp.notification.domain.NotificationDeviceToken
import org.springframework.stereotype.Component

@Component
class PushClientApplication(
    private val tokenRepository: NotificationDeviceTokenRepository
) {

    fun send(tokenEntity: NotificationDeviceToken, message: Message) {
        try {
            FirebaseMessaging.getInstance().send(message)
        } catch (e: FirebaseMessagingException) {
            if (e.messagingErrorCode == MessagingErrorCode.UNREGISTERED) {

                if (tokenEntity.deviceId == null || tokenEntity.deviceId!!.isBlank()) {
                    throw IllegalArgumentException("deviceId must not be blank")
                }

                tokenRepository.deleteByMemberIdAndDeviceId(
                    tokenEntity.memberId,
                    tokenEntity.deviceId!!
                )
            }
            throw e
        }
    }


    fun sendWithDeviceId(deviceId: String, memberId: Long, message: Message) {
        try {
            FirebaseMessaging.getInstance().send(message)
        } catch (e: FirebaseMessagingException) {
            if (e.messagingErrorCode == MessagingErrorCode.UNREGISTERED) {

                if (deviceId.isBlank()) {
                    throw IllegalArgumentException("deviceId must not be blank")
                }

                tokenRepository.deleteByMemberIdAndDeviceId(
                    memberId,
                    deviceId
                )
            }
            throw e
        }

    }
}