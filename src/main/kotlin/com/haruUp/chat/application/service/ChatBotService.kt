package com.haruUp.chat.application.service

import com.haruUp.category.domain.dto.JobDto
import com.haruUp.chat.domain.ChatState
import org.springframework.stereotype.Service

@Service
class ChatBotService {

    /**
     * 첫 진입 시 보여줄 소개 문구 생성
     */
    fun getIntroMessage(): String {
        return """
            안녕하세요! 저는 당신의 학습 도우미 하루예요.
            가장 적합한 커리큘럼을 설계하기 위해 몇 가지 간단한 질문을 드릴게요.
            
            어떤 자기계발을 하고 싶으신가요?
        """.trimIndent()
    }

    /**
     * 상위 관심사에 따른 세부 관심사 질문 문구 생성
     */
    fun getSubCategoryQuestion(category: String): String {
        return when (category) {
            "외국어 공부" -> "어떤 외국어를 공부하고 싶으신가요?"
            "재테크 및 투자" -> "어떤 재테크나 투자를 하고 싶으신가요?"
            "체력관리 및 운동" -> "어떤 운동을 하고 싶으신가요?"
            "자격증 공부" -> "어떤 자격증을 취득하고 싶으신가요?"
            "직무 관련 역량 개발" -> "어떤 직무 관련 역량을 개발하고 싶으신가요?"
            else -> "어떤 분야를 배우고 싶으신가요?"
        }
    }

    /**
     * 학습 동기 질문 문구 생성
     *
     * 상위 관심사와 세부 관심사에 따라
     * 자연스러운 질문 문장을 만든다.
     */
    fun getMotivationQuestion(category: String, subCategory: String): String {
        return when (category) {
            "외국어 공부" -> "${subCategory}를 배우려는 구체적인 동기가 무엇인가요?"
            "재테크 및 투자" -> "${subCategory}을/를 하려는 구체적인 동기가 무엇인가요?"
            "체력관리 및 운동" -> "${subCategory}을/를 하려는 특별한 동기가 무엇인가요?"
            "자격증 공부" -> "${subCategory}을/를 취득하려는 구체적인 동기가 무엇인가요?"
            "직무 관련 역량 개발" -> "${subCategory}을/를 개발하려는 구체적인 동기가 무엇인가요?"
            else -> "${subCategory}를 선택한 이유를 알려주세요."
        }
    }

    /**
     * 최종 요약 메시지 생성
     *
     * 현재는 수집한 정보를 정리해서 보여주는 형태로 구현한다.
     * 이후 필요하면 이 부분을 실제 추천 문구 생성 로직으로 확장할 수 있다.
     */
    fun buildFinalMessage(state: ChatState): String {
        val extra = if (state.followUpAnswer.isNullOrBlank()) {
            ""
        } else {
            "\n- 추가 정보: ${state.followUpAnswer}"
        }

        return """
            좋아요. 입력해주신 내용을 정리해볼게요.

            - 관심 분야: ${state.category}
            - 세부 관심사: ${state.subCategory}
            - 학습 동기: ${state.motivation}$extra

            이 정보를 바탕으로 맞춤형 커리큘럼을 설계해드릴게요.
        """.trimIndent()
    }

    /**
     * 상위 관심사 검증
     *
     * 사용자가 입력한 category가 DB에서 조회한 목록에 포함되는지 확인한다.
     */
    fun isValidCategory(answer: String, options: List<JobDto>): Boolean {
        val answerId = answer.toLongOrNull() ?: return false
        return options.any { it.id == answerId }
    }

    /**
     * 세부 관심사 검증
     *
     * 사용자가 입력한 subCategory가 DB에서 조회한 목록에 포함되는지 확인한다.
     */
    fun isValidSubCategory(answer: String, options: List<String>): Boolean {
        return options.contains(answer)
    }

    /**
     * ChatState 초기화
     *
     * 상태가 꼬였을 때 처음부터 다시 시작할 수 있도록
     * 필요한 필드를 초기 상태로 되돌린다.
     */
    fun resetState(state: ChatState) {
        state.depth = 1
        state.category = null
        state.subCategory = null
        state.motivation = null
        state.needFollowUp = false
        state.followUpQuestion = null
        state.followUpAnswer = null
    }
}