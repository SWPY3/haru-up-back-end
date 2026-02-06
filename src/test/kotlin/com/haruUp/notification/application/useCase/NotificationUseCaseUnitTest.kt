package com.haruUp.notification.application.useCase

import com.google.firebase.messaging.Message
import com.haruUp.notification.application.PushClient
import com.haruUp.notification.application.service.NotificationTokenService
import com.haruUp.notification.domain.NotificationDeviceToken
import com.haruUp.notification.domain.PushPlatform
import com.haruUp.notification.infrastructure.PushClientApplication
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any

@ExtendWith(MockitoExtension::class)
class NotificationUseCaseTest {

    @Mock
    lateinit var notificationTokenService: NotificationTokenService

    @Mock
    lateinit var pushClient: PushClient

    @Mock
    lateinit var pushClientApplication: PushClientApplication

    private lateinit var notificationUseCase: NotificationUseCase

    @BeforeEach
    fun setUp() {
        notificationUseCase = NotificationUseCase(
            notificationTokenService = notificationTokenService,
            pushClient = pushClient,
            pushClientApplication = pushClientApplication
        )
    }

    @Test
    fun `sendToMember는 토큰 개수만큼 메시지를 생성하고 전송한다`() {
        // given
        val token1 = NotificationDeviceToken(
            memberId = 1L,
            deviceId = "device-1",
            platform = PushPlatform.IOS,
            token = "token-1"
        )

        val token2 = NotificationDeviceToken(
            memberId = 1L,
            deviceId = "device-2",
            platform = PushPlatform.IOS,
            token = "token-2"
        )

        `when`(notificationTokenService.getTokensByMember(1L))
            .thenReturn(listOf(token1, token2))

        val mockMessage = mock(Message::class.java)

        // ✅ 실제 시그니처에 맞게 인자 3개
        `when`(pushClient.createMessage(any(), any(), any()))
            .thenReturn(mockMessage)

        // when
        notificationUseCase.sendToMember(
            memberId = 1L,
            title = "제목",
            body = "내용"
        )

        // then
        verify(notificationTokenService).getTokensByMember(1L)

        // 토큰 2개 → 메시지 2번 생성
        verify(pushClient, times(2))
            .createMessage(any(), any(), any())

        // 토큰 2개 → 전송 2번
        verify(pushClientApplication, times(2))
            .send(any(), any())
    }

    @Test
    fun `토큰이 없으면 푸시 전송을 시도하지 않는다`() {
        // given
        `when`(notificationTokenService.getTokensByMember(1L))
            .thenReturn(emptyList())

        // when
        notificationUseCase.sendToMember(
            memberId = 1L,
            title = "제목",
            body = "내용"
        )

        // then
        verify(pushClient, never())
            .createMessage(any(), any(), any())

        verify(pushClientApplication, never())
            .send(any(), any())
    }
}