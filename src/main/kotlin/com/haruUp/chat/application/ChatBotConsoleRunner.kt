package com.haruUp.chat.application

import com.haruUp.chat.domain.ChatRequest
import com.haruUp.chat.domain.ChatResponse
import org.springframework.boot.CommandLineRunner
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.util.Scanner
import java.util.UUID

@Component
@Profile("chat-console")
class ChatBotConsoleRunner(
    private val chatBotUseCase: ChatBotUseCase
) : CommandLineRunner {

    override fun run(vararg args: String?) {
        val scanner = Scanner(System.`in`)
        var sessionId = UUID.randomUUID().toString()

        println("========================================")
        println(" 챗봇 콘솔 테스트 시작")
        println(" sessionId = $sessionId")
        println(" 종료: exit | quit | 종료")
        println(" 초기화: reset")
        println("========================================")

        var response = chatBotUseCase.chatWithBot(
            ChatRequest(
                sessionId = sessionId,
                content = ""
            )
        )
        printResponse(response)

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
                    println("\n세션을 초기화했습니다.")
                    println("sessionId = $sessionId")
                    response = chatBotUseCase.chatWithBot(
                        ChatRequest(
                            sessionId = sessionId,
                            content = ""
                        )
                    )
                    printResponse(response)
                    continue
                }
            }

            response = chatBotUseCase.chatWithBot(
                ChatRequest(
                    sessionId = sessionId,
                    content = input
                )
            )
            printResponse(response)

            if (response.completed) {
                println("\n대화가 완료되었습니다. 새 대화를 자동으로 시작합니다.")
                sessionId = UUID.randomUUID().toString()
                response = chatBotUseCase.chatWithBot(
                    ChatRequest(
                        sessionId = sessionId,
                        content = ""
                    )
                )
                printResponse(response)
            }
        }

        scanner.close()
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
}
