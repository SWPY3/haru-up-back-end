package com.haruUp.notification.domain

data class MissionPushTarget(
    val memberId: Long,
    val deviceId: String
)