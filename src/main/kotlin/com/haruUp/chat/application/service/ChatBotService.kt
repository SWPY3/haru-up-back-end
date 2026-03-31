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
            
            어떤 관심 분야를 중심으로 성장하고 싶으신가요?
        """.trimIndent()
    }

    fun getCategoryQuestion(): String {
        return "어떤 관심 분야를 중심으로 성장하고 싶으신가요?"
    }

    fun getRestartMessage(): String {
        return "처음부터 다시 시작할게요. ${getCategoryQuestion()}"
    }

    /**
     * 상위 관심사에 따른 세부 관심사 질문 문구 생성
     */
    fun getSubCategoryQuestion(category: String): String {
        return "${category} 안에서 더 구체적으로 집중하고 싶은 세부 관심사나 직무를 선택해주세요."
    }

    /**
     * 학습 목표 질문 문구 생성
     */
    fun getGoalQuestion(category: String, subCategory: String): String {
        return "${subCategory}와 관련해 지금 가장 이루고 싶은 목표는 무엇인가요?"
    }

    fun getDesiredOutcomeQuestion(subCategory: String): String {
        return "이번 ${subCategory} 목표에서 최종적으로 만들고 싶은 결과물은 무엇인가요? 예: 기획서 1장, 와이어프레임 3장, 앱 MVP"
    }

    fun getSkillLevelQuestion(subCategory: String): String {
        return "${subCategory} 기준으로 현재 실력은 어느 정도인가요? 예: 입문, 기초, 중급, 실무 경험 있음"
    }

    fun getRecentExperienceQuestion(subCategory: String): String {
        return "최근 직접 해본 ${subCategory} 관련 작업이 있나요? 없으면 '아직 해본 적 없음'이라고 적어주세요. 예: 화면 1개 손그림, Figma 초안 1개, 문서 1장 작성"
    }

    fun getTargetPeriodQuestion(): String {
        return "이 목표를 어느 정도 기간 안에 이루고 싶으신가요? 예: 1개월, 3개월, 6개월"
    }

    fun getDailyAvailableTimeQuestion(): String {
        return "하루에 이 목표를 위해 어느 정도 시간을 투자할 수 있나요? 예: 30분, 1시간, 2시간"
    }

    fun getAdditionalOpinionQuestion(): String {
        return "추가로 반영했으면 하는 점이 있나요? 없으면 '없음'이라고 입력해주세요."
    }

    /**
     * 최종 요약 메시지 생성
     *
     * 현재는 수집한 정보를 정리해서 보여주는 형태로 구현한다.
     * 이후 필요하면 이 부분을 실제 추천 문구 생성 로직으로 확장할 수 있다.
     */
    fun buildFinalMessage(state: ChatState): String {
        val summaryLines = mutableListOf(
            "좋아요. 입력해주신 내용을 정리해볼게요.",
            "",
            "- 관심 분야: ${state.category}",
            "- 세부 관심사: ${state.subCategory}",
            "- 현재 목표: ${state.goal}",
            "- 최종 결과물: ${state.desiredOutcome}",
            "- 현재 실력: ${state.skillLevel}",
            "- 최근 직접 해본 작업: ${state.recentExperience}",
            "- 목표 기간: ${state.targetPeriod}",
            "- 하루 투자 가능 시간: ${state.dailyAvailableTime}"
        )

        if (!state.additionalOpinion.isNullOrBlank()) {
            summaryLines.add("- 추가 의견: ${state.additionalOpinion}")
        }

        summaryLines.add("")
        summaryLines.add("이 정보를 바탕으로 맞춤형 커리큘럼을 설계해드릴게요.")

        return summaryLines.joinToString("\n")
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
        state.categoryNo = null
        state.category = null
        state.subCategoryNo = null
        state.subCategory = null
        state.motivation = null
        state.goal = null
        state.desiredOutcome = null
        state.skillLevel = null
        state.recentExperience = null
        state.targetPeriod = null
        state.dailyAvailableTime = null
        state.additionalOpinion = null
        state.needFollowUp = false
        state.followUpQuestion = null
        state.followUpAnswer = null
    }
}
