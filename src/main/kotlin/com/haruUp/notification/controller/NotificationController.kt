// src/main/kotlin/com/swyp/notification/controller/NotificationController.kt
package com.haruUp.notification.controller

import com.haruUp.global.common.ApiResponse
import com.haruUp.notification.application.useCase.NotificationUseCase
import com.haruUp.notification.application.service.NotificationTokenService
import com.haruUp.notification.domain.PushPlatform
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/notifications")
@Tag(name = "Notification", description = "알림/푸시 관련 API")
class NotificationController(
    private val notificationTokenService : NotificationTokenService,
    private val notificationUseCase: NotificationUseCase
) {

    @Operation(summary = "푸시 토큰 등록")
    @PostMapping("/token")
    fun registerToken(
        @RequestBody request: RegisterPushTokenRequest
    ): ApiResponse<Unit> {
        // TODO: 실제 구현에선 memberId 를 JWT에서 가져오도록 변경
        notificationTokenService.registerToken(
            memberId = request.memberId,
            deviceId = request.deviceId,
            platform = request.platform,
            token = request.token
        )

        return ApiResponse.success(Unit)
    }

    @Operation(summary = "단일 사용자 테스트 푸시 발송")
    @PostMapping("/test/send")
    fun sendTest(
        @RequestBody request: SendTestNotificationRequest
    ): ApiResponse<Unit> {
        notificationUseCase.sendToMember(
            memberId = request.memberId,
            title = request.title,
            body = request.body,
            data = request.data ?: emptyMap()
        )
        return ApiResponse.success(Unit)
    }
}

data class RegisterPushTokenRequest(
    val memberId: Long,               // 🔥 실제로는 JWT에서 꺼내 쓰는 걸 추천
    val deviceId: String,
    val platform: PushPlatform,
    val token: String
)

data class SendTestNotificationRequest(
    val memberId: Long,
    val title: String,
    val body: String,
    val data: Map<String, String>? = null
)
