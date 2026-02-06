package com.haruUp.notification.infrastructure

import com.haruUp.notification.domain.NotificationDeviceToken
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface NotificationDeviceTokenRepository : JpaRepository<NotificationDeviceToken, Long>{

    @Query("""
        SELECT n 
        FROM NotificationDeviceToken n
        INNER JOIN MemberSetting ms  ON n.memberId = ms.memberId
        WHERE n.memberId = :memberId AND n.deviceId = :deviceId
        AND ms.pushEnabled = true
    """)
    fun findByMemberIdAndDeviceId(memberId: Long, deviceId: String): NotificationDeviceToken?

    fun findAllByMemberId(memberId: Long): List<NotificationDeviceToken>

    fun deleteByMemberIdAndDeviceId(memberId: Long, deviceId: String)

    fun deleteAllByMemberId(memberId: Long)

    @Modifying
    @Query("""    
        UPDATE NotificationDeviceToken n
        SET n.token = :token
        WHERE n.memberId = :memberId AND n.deviceId = :deviceId
    """)
    fun updateToken(memberId: Long, token: String, deviceId: String): NotificationDeviceToken

    @Modifying
    fun deleteByToken(token: String)


}