package com.haruUp.notification.infrastructure

import com.haruUp.member.domain.QMemberSetting.memberSetting
import com.haruUp.notification.domain.NotificationDeviceToken
import com.haruUp.notification.domain.QNotificationDeviceToken.notificationDeviceToken
import com.querydsl.jpa.impl.JPAQueryFactory
import jakarta.persistence.EntityManager
import org.springframework.transaction.annotation.Transactional

open class NotificationDeviceTokenRepositoryImpl(
    private val entityManager: EntityManager
) : NotificationDeviceTokenRepositoryCustom {
    private val queryFactory = JPAQueryFactory(entityManager)

    override fun findByMemberIdAndDeviceId(memberId: Long, deviceId: String): NotificationDeviceToken? {
        return queryFactory
            .selectFrom(notificationDeviceToken)
            .join(memberSetting)
            .on(notificationDeviceToken.memberId.eq(memberSetting.memberId))
            .where(
                notificationDeviceToken.memberId.eq(memberId),
                notificationDeviceToken.deviceId.eq(deviceId),
                memberSetting.pushEnabled.isTrue
            )
            .fetchOne()
    }

    @Transactional
    override fun updateToken(memberId: Long, token: String, deviceId: String): NotificationDeviceToken {
        queryFactory
            .update(notificationDeviceToken)
            .set(notificationDeviceToken.token, token)
            .where(
                notificationDeviceToken.memberId.eq(memberId),
                notificationDeviceToken.deviceId.eq(deviceId)
            )
            .execute()

        // Bulk update bypasses persistence context; clear it to avoid stale entity reads.
        entityManager.flush()
        entityManager.clear()

        return queryFactory
            .selectFrom(notificationDeviceToken)
            .where(
                notificationDeviceToken.memberId.eq(memberId),
                notificationDeviceToken.deviceId.eq(deviceId)
            )
            .fetchOne()
            ?: throw IllegalStateException("NotificationDeviceToken not found for memberId=$memberId, deviceId=$deviceId")
    }
}
