package com.haruUp.chat.controller

import com.haruUp.chat.application.service.ChatBotService
import com.haruUp.chat.domain.ChatDto
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.handler.annotation.SendTo
import org.springframework.stereotype.Controller

@Controller
class ChatController(
    private val chatBotService: ChatBotService
) {

    @MessageMapping("/chat.send")
    @SendTo("/topic/public")
    fun sendMessage(message: ChatDto): ChatDto? {
        println("user message = ${message.content}")

        val answer = message.content?.let { chatBotService.askChatBot(it) }

        println("bot answer = $answer")

        return answer?.let {
            ChatDto(
                content = it,
                role = "BOT"
            )
        }
    }
}