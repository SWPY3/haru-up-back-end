package com.haruUp.chat.controller

import com.haruUp.chat.application.ChatBotUseCase
import com.haruUp.chat.application.service.ChatBotMissionContext
import com.haruUp.chat.application.service.ChatBotMissionRecommendationService
import com.haruUp.chat.domain.ChatRequest
import com.haruUp.chat.domain.ChatResponse
import com.haruUp.missionembedding.dto.MissionDto
import kotlinx.coroutines.runBlocking
import org.springframework.context.annotation.Profile
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@Profile("chat-browser")
@RequestMapping("/api/chat")
class ChatBrowserTestController(
    private val chatBotUseCase: ChatBotUseCase,
    private val chatBotMissionRecommendationService: ChatBotMissionRecommendationService
) {

    @PostMapping("/test")
    fun chat(@RequestBody request: ChatRequest): ChatResponse {
        return chatBotUseCase.chatWithBot(request)
    }

    @PostMapping("/test/recommend")
    fun recommend(@RequestBody request: ChatBotMissionContext): List<MissionDto> {
        return runBlocking {
            chatBotMissionRecommendationService.recommend(request)
        }
    }
}
