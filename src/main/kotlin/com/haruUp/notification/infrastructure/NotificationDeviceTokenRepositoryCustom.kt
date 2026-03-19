package com.haruUp.notification.infrastructure

import com.haruUp.notification.domain.NotificationDeviceToken

interface NotificationDeviceTokenRepositoryCustom {
    fun findByMemberIdAndDeviceId(memberId: Long, deviceId: String): NotificationDeviceToken?

    fun updateToken(memberId: Long, token: String, deviceId: String): NotificationDeviceToken
}
