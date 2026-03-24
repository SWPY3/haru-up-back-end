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
class ChatBotUseCaseTest(
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

        // 첫 진입 호출
        printResponse(
            chatBotUseCase.chatWithBot(
                ChatRequest(
                    sessionId = sessionId,
                    content = ""
                )
            )
        )

        while (true) {
            print("\n사용자 입력 > ")
            val input = scanner.nextLine()

            when (input.trim().lowercase()) {
                "exit", "quit", "종료" -> {
                    println("챗봇 콘솔 테스트를 종료합니다.")
                    break
                }

                "reset" -> {
                    sessionId = UUID.randomUUID().toString()
                    println("\n세션을 초기화했습니다.")
                    println("새 sessionId = $sessionId")

                    printResponse(
                        chatBotUseCase.chatWithBot(
                            ChatRequest(
                                sessionId = sessionId,
                                content = ""
                            )
                        )
                    )
                    continue
                }
            }

            val response = chatBotUseCase.chatWithBot(
                ChatRequest(
                    sessionId = sessionId,
                    content = input
                )
            )

            printResponse(response)

            // 대화 완료 시 새 세션으로 다시 시작 가능하도록 처리
            if (response.completed) {
                println("\n대화가 완료되었습니다. 새 대화를 시작하려면 아무 값이나 입력하세요.")
                sessionId = UUID.randomUUID().toString()
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