package com.haruUp.notification.application.service

import com.haruUp.notification.domain.PushPlatform
import com.haruUp.notification.infrastructure.NotificationDeviceTokenRepository
import jakarta.transaction.Transactional
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class NotificationTokenServiceIntegrationTest( ) {

    @Autowired
    lateinit var notificationRepository: NotificationDeviceTokenRepository

    @Autowired
    lateinit var notificationTokenService: NotificationTokenService

    @Test
    fun `기존 토큰이 있으면 DB에서 token과 platform이 갱신된다`(){
        //given
        notificationRepository.save(
            com.haruUp.notification.domain.NotificationDeviceToken(
                memberId = 2L,
                deviceId = "device123",
                platform = PushPlatform.IOS,
                token = "old_token"
            )
        )

        //when
            notificationTokenService.registerToken(
            memberId = 2L,
            deviceId = "device123",
            platform = PushPlatform.ANDROID,
            token = "new_token"
        )

        //than
        val result = notificationRepository.findByMemberIdAndDeviceId(2L, "device123")
        assertEquals("new_token", result?.token)
        assertEquals(PushPlatform.ANDROID, result?.platform )
    }


    @Test
    fun `기존 토큰이 없으면 DB에 새 row가 저장된다`(){
        //given
        notificationTokenService.registerToken(
            memberId = 2L,
            deviceId = "device456",
            platform = PushPlatform.IOS,
            token = "fresh_token"
        )

        //when
        val result = notificationRepository.findByMemberIdAndDeviceId(2L, "device456")

        //than
        assertNotNull(result)
        assertEquals("fresh_token", result?.token)
        assertEquals(PushPlatform.IOS, result?.platform )
    }










}