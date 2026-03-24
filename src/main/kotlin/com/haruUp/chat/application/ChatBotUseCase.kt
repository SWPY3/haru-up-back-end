package com.haruUp.chat.application

import com.haruUp.chat.application.service.ChatBotService
import com.haruUp.chat.domain.ChatDto
import com.haruUp.global.clova.ClovaApiClient
import com.haruUp.interest.service.MemberInterestService

class ChatBotUseCase(
    private final var chatBotService: ChatBotService,
    private final var clovaApiClient: ClovaApiClient,
    private final var interestService: MemberInterestService
) {



   // 쳇봇 구현
    fun chatWithBot(userMessage: ChatDto): String {

        if(userMessage.depth == 0) {
            // 첫 번째 질문에 대한 로직을 구현
            // 예: 사용자의 직업상세 정보를 기반으로 관심사 추천 시작
            return ""
        } else {
            // 이후 질문에 대한 로직을 구현
            // 예: 이전 답변을 기반으로 추가 관심사 추천
        }

        // 챗봇에게 질문을 보내고 답변을 받는 로직을 구현
        val botResponse = askChatBot(userMessage.content)

        // 사용자 답변의 유효성을 검증하는 로직을 구현
        if (validateAnswer(botResponse)) {
            return botResponse
        } else {
            return "죄송합니다. 이해하지 못했습니다. 다시 시도해주세요."
        }
    }


    // depth에 따른 쳇봇 답변
    fun getBotResponseByDepth(depth: Int, userMessage: String): String {
        return when (depth) {
            0 -> {
                // 첫 번째 질문에 대한 답변 로직
                "첫 번째 질문에 대한 답변입니다."
            }
            1 -> {
                // 두 번째 질문에 대한 답변 로직
                "두 번째 질문에 대한 답변입니다."
            }
            else -> {
                // 그 이후 질문에 대한 답변 로직
                "그 이후 질문에 대한 답변입니다."
            }
        }
    }


    // 챗봇에게 질문을 보내고 답변을 받는 로직을 구현
    fun askChatBot(question: String): String {

        interestService.getIntersetsList();

        return "";
    }


    // 사용자 답변의 유효성을 검증하는 로직을 구현
    private fun validateAnswer(answer: String): Boolean {
        var prompt = buildPrompt();

        val response = clovaApiClient.generateText(
            userMessage = prompt,
            systemMessage = CHAT_BOT_PROMPT
        )

        return response.contains("OK");
    }

    private fun buildPrompt() : String {

        return "";
    }


    companion object {
        private const val CHAT_BOT_PROMPT = """
            관심사 추천 전문가입니다.

            ## 핵심 규칙
            1. 대중적이고 보편적인 관심사를 우선 추천 (많은 사람들이 관심 가질 만한 것)
            2. 직업상세가 있으면 해당 직업에 특화된 세부 분야를 추천
               - 예: 직업상세 "요리사" + "직무 전문 분야" → 한식, 중식, 일식, 양식 등
               - 예: 직업상세 "개발자" + "직무 전문 분야" → 백엔드, 프론트엔드, 클라우드 등
            3. 제외 항목은 절대 추천 금지
            4. 요청된 개수만큼 정확히 추천
            5. 각 항목은 2-10자 내 간결하게

            ## 응답 형식
            {"interest": ["항목1", "항목2", ...]}
            """
    }


}