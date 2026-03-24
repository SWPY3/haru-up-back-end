package com.haruUp.chat.controller

import com.haruUp.chat.application.ChatBotUseCase
import com.haruUp.chat.application.service.ChatBotService
import com.haruUp.chat.domain.ChatDto
import com.haruUp.chat.domain.ChatRequest
import com.haruUp.chat.domain.ChatResponse
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.handler.annotation.SendTo
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.RequestBody

@Controller
class ChatController(
    private val chatBotUseCase: ChatBotUseCase
) {

    @MessageMapping("/chat.send")
    @SendTo("/topic/public")
    fun chat(@RequestBody request: ChatRequest): ChatResponse {
        return chatBotUseCase.chatWithBot(request)
    }

}