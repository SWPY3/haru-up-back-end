package com.haruUp.notification.infrastructure

import com.haruUp.notification.application.PushClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class PushClientApplication : PushClient {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun sendToToken(
        token: String,
        title: String,
        body: String,
        data: Map<String, String>
    ) {
        log.info(
            "📨 [DUMMY PUSH] token={}, title={}, body={}, data={}",
            token, title, body, data
        )
    }
}