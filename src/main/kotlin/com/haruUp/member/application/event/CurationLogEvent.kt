package com.haruUp.member.application.event

import java.time.LocalDateTime

data class CurationLogEvent(
    val step: String,
    val message: String,
    val donaAt : LocalDateTime = LocalDateTime.now()
)
