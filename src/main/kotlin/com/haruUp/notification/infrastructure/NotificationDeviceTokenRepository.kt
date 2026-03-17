package com.haruUp.notification.infrastructure

import com.haruUp.notification.domain.NotificationDeviceToken
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying

interface NotificationDeviceTokenRepository :
    JpaRepository<NotificationDeviceToken, Long>,
    NotificationDeviceTokenRepositoryCustom {

    fun findAllByMemberId(memberId: Long): List<NotificationDeviceToken>

    fun deleteByMemberIdAndDeviceId(memberId: Long, deviceId: String)

    fun deleteAllByMemberId(memberId: Long)

    @Modifying
    fun deleteByToken(token: String)
}
