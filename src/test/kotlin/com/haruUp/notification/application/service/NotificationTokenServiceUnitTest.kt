package com.haruUp.notification.application.service

import com.haruUp.notification.domain.NotificationDeviceToken
import com.haruUp.notification.domain.PushPlatform
import com.haruUp.notification.infrastructure.NotificationDeviceTokenRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

@ExtendWith(MockitoExtension::class)
class NotificationTokenServiceUnitTest {


    @Mock
    lateinit var notificationRepository: NotificationDeviceTokenRepository

    private lateinit var notificationTokenService: NotificationTokenService


    @BeforeEach
    fun setUp() {
        notificationTokenService = NotificationTokenService(notificationRepository)
    }


    @Test
    fun `기존 토큰이 있으면 token과 platform을 갱신한다`() {
        //given
        val existing = NotificationDeviceToken(
            memberId = 100L,
            deviceId = "device123",
            platform = PushPlatform.IOS,
            token = "old_token"
        )

        `when`(
            notificationRepository.findByMemberIdAndDeviceId(100L, "device123")
        ).thenReturn(existing)

        //when
        notificationTokenService.registerToken(
            memberId = 100L,
            deviceId = "device123",
            platform = PushPlatform.ANDROID,
            token = "new_token"
        )

        //than
        assertEquals("new_token", existing.token)
        assertEquals(PushPlatform.ANDROID, existing.platform)


        verify(notificationRepository, never()).save(any())
    }


    @Test
    fun `기존 토큰이 없으면 새 토큰을 저장한다`() {
        //given
        `when`(
            notificationRepository.findByMemberIdAndDeviceId(100L, "device123")
        ).thenReturn(null)


        //when
        notificationTokenService.registerToken(
            memberId = 100L,
            deviceId = "device123",
            platform = PushPlatform.ANDROID,
            token = "new_token"
        )

        //than
        verify(notificationRepository).save(
            argThat {
                this.memberId == 100L &&
                this.deviceId == "device123" &&
                this.platform == PushPlatform.ANDROID &&
                this.token == "new_token"
            }
        )
    }



    @Test
    fun `deviceId가 비어 있으면 예외가 발생한다`(){
        //expect
        assertThrows(IllegalArgumentException::class.java) {
            //when
            notificationTokenService.registerToken(
                memberId = 100L,
                deviceId = "   ",
                platform = PushPlatform.ANDROID,
                token = "new_token"
            )
        }

    }


    @Test
    fun `token이 비어있으면 예외가 발생한다`(){
        //expect
        assertThrows(IllegalArgumentException::class.java) {
            //when
            notificationTokenService.registerToken(
                memberId = 100L,
                deviceId = "device123",
                platform = PushPlatform.ANDROID,
                token = "   "
            )
        }
    }





}