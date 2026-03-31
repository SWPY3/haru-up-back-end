package com.haruUp.chat.application

import com.haruUp.chat.application.service.ChatBotMissionContext
import com.haruUp.chat.application.service.ChatBotMissionRecommendationService
import com.haruUp.chat.domain.ChatOptionType
import com.haruUp.chat.domain.ChatRequest
import com.haruUp.chat.domain.ChatResponse
import kotlinx.coroutines.runBlocking
import org.springframework.boot.CommandLineRunner
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.util.Scanner
import java.util.UUID

@Component
@Profile("chat-console")
class ChatBotConsoleRunner(
    private val chatBotUseCase: ChatBotUseCase,
    private val chatBotMissionRecommendationService: ChatBotMissionRecommendationService
) : CommandLineRunner {

    override fun run(vararg args: String?) {
        val scanner = Scanner(System.`in`)
        var sessionId = UUID.randomUUID().toString()
        val chatContext = ChatConsoleContext()

        println("========================================")
        println(" 챗봇 콘솔 테스트 시작")
        println(" sessionId = $sessionId")
        println(" 종료: exit | quit | 종료")
        println(" 초기화: reset")
        println("========================================")

        var lastResponse = chatBotUseCase.chatWithBot(
            ChatRequest(
                sessionId = sessionId,
                content = ""
            )
        )

        printResponse(
            lastResponse
        )

        while (true) {
            print("\n사용자 입력 > ")
            if (!scanner.hasNextLine()) {
                println("\n입력이 종료되어 챗봇 콘솔 테스트를 종료합니다.")
                break
            }

            val input = scanner.nextLine()

            when (input.trim().lowercase()) {
                "exit", "quit", "종료" -> {
                    println("챗봇 콘솔 테스트를 종료합니다.")
                    break
                }

                "reset" -> {
                    sessionId = UUID.randomUUID().toString()
                    chatContext.reset()
                    println("\n세션을 초기화했습니다.")
                    println("새 sessionId = $sessionId")

                    lastResponse = chatBotUseCase.chatWithBot(
                        ChatRequest(
                            sessionId = sessionId,
                            content = ""
                        )
                    )

                    printResponse(lastResponse)
                    continue
                }
            }

            captureInput(lastResponse, input, chatContext)

            val response = chatBotUseCase.chatWithBot(
                ChatRequest(
                    sessionId = sessionId,
                    content = input
                )
            )

            printResponse(response)
            lastResponse = response

            if (response.completed) {
                printMissionRecommendations(chatContext)

                println("\n대화가 완료되었습니다. 새 대화를 바로 시작할게요.")
                sessionId = UUID.randomUUID().toString()
                chatContext.reset()

                lastResponse = chatBotUseCase.chatWithBot(
                    ChatRequest(
                        sessionId = sessionId,
                        content = ""
                    )
                )

                printResponse(lastResponse)
            }
        }

        scanner.close()
    }

    private fun captureInput(
        lastResponse: ChatResponse,
        input: String,
        chatContext: ChatConsoleContext
    ) {
        when (lastResponse.nextDepth) {
            1 -> {
                val selectedNo = input.trim().toLongOrNull() ?: return
                val selectedOption = lastResponse.options.find {
                    it.no == selectedNo && it.type == ChatOptionType.JOB
                } ?: return

                chatContext.category = selectedOption.label
                chatContext.subCategory = null
                chatContext.goal = null
                chatContext.desiredOutcome = null
                chatContext.skillLevel = null
                chatContext.recentExperience = null
                chatContext.targetPeriod = null
                chatContext.dailyAvailableTime = null
                chatContext.additionalOpinion = null
            }

            2 -> {
                val selectedNo = input.trim().toLongOrNull() ?: return
                val selectedOption = lastResponse.options.find {
                    it.no == selectedNo && it.type == ChatOptionType.JOB_DETAIL
                } ?: return

                chatContext.subCategory = selectedOption.label
            }

            3 -> chatContext.goal = input.trim()
            4 -> chatContext.desiredOutcome = input.trim()
            5 -> chatContext.skillLevel = input.trim()
            6 -> chatContext.recentExperience = input.trim()
            7 -> chatContext.targetPeriod = input.trim()
            8 -> chatContext.dailyAvailableTime = input.trim()
            9 -> chatContext.additionalOpinion = input.trim()
        }
    }

    private fun printMissionRecommendations(chatContext: ChatConsoleContext) {
        val category = chatContext.category
        val subCategory = chatContext.subCategory
        val goal = chatContext.goal
        val desiredOutcome = chatContext.desiredOutcome
        val skillLevel = chatContext.skillLevel
        val recentExperience = chatContext.recentExperience
        val targetPeriod = chatContext.targetPeriod
        val dailyAvailableTime = chatContext.dailyAvailableTime

        if (
            category.isNullOrBlank() ||
            subCategory.isNullOrBlank() ||
            goal.isNullOrBlank() ||
            desiredOutcome.isNullOrBlank() ||
            skillLevel.isNullOrBlank() ||
            recentExperience.isNullOrBlank() ||
            targetPeriod.isNullOrBlank() ||
            dailyAvailableTime.isNullOrBlank()
        ) {
            println("\n[추천 미션]")
            println("추천에 필요한 선택 정보가 부족해서 미션 추천을 건너뜁니다.")
            return
        }

        val recommendedMissions = runBlocking {
            chatBotMissionRecommendationService.recommend(
                ChatBotMissionContext(
                    category = category,
                    subCategory = subCategory,
                    goal = goal,
                    desiredOutcome = desiredOutcome,
                    skillLevel = skillLevel,
                    recentExperience = recentExperience,
                    targetPeriod = targetPeriod,
                    dailyAvailableTime = dailyAvailableTime,
                    additionalOpinion = chatContext.additionalOpinion
                )
            )
        }

        println("\n[추천 미션]")
        println("추천 기준 경로 = $category > $subCategory")

        if (recommendedMissions.isEmpty()) {
            println("추천 결과가 비어 있습니다.")
            return
        }

        recommendedMissions.forEachIndexed { index, mission ->
            println(
                "${index + 1}. difficulty=${mission.difficulty}, exp=${mission.expEarned}, content=${mission.content}"
            )
        }
    }

    private fun printResponse(response: ChatResponse) {
        println("\n[BOT]")
        println(response.message)
        println("nextDepth = ${response.nextDepth}")
        println("completed = ${response.completed}")

        if (response.options.isNotEmpty()) {
            println("\n[선택 가능한 항목]")
            response.options.forEach { option ->
                println("- no=${option.no}, label=${option.label}, type=${option.type}")
            }
        }
    }

    private data class ChatConsoleContext(
        var category: String? = null,
        var subCategory: String? = null,
        var goal: String? = null,
        var desiredOutcome: String? = null,
        var skillLevel: String? = null,
        var recentExperience: String? = null,
        var targetPeriod: String? = null,
        var dailyAvailableTime: String? = null,
        var additionalOpinion: String? = null
    ) {
        fun reset() {
            category = null
            subCategory = null
            goal = null
            desiredOutcome = null
            skillLevel = null
            recentExperience = null
            targetPeriod = null
            dailyAvailableTime = null
            additionalOpinion = null
        }
    }
}
