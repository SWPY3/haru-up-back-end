package com.haruUp.chat.domain

import lombok.Data

@Data
data class ChatState (
    /**
     * 현재 대화 단계
     *
     * 예시:
     * 0 = 첫 진입
     * 1 = 상위 관심사 선택 단계
     * 2 = 세부 관심사 선택 단계
     * 3 = 목표 입력 단계
     * 4 = 최종 결과물 입력 단계
     * 5 = 현재 실력 입력 단계
     * 6 = 최근 직접 해본 작업 입력 단계
     * 7 = 목표 기간 입력 단계
     * 8 = 하루 투자 가능 시간 입력 단계
     * 9 = 추가 의견 입력 단계
     * 999 = 종료
     */
    var depth: Int = 0,

    /**
     * 상위 관심사
     *
     * 예:
     * - 외국어 공부
     * - 재테크 및 투자
     * - 체력관리 및 운동
     */
    var categoryNo: Long? = null,
    var category: String? = null,

    /**
     * 상위 관심사
     *
     * 예:
     * - 외국어 공부
     * - 재테크 및 투자
     * - 체력관리 및 운동
     */
    var subCategoryNo: Long? = null,
    var subCategory: String? = null,

    /**
     * 이전 질문 구조와의 호환을 위해 유지하는 필드
     */
    var motivation: String? = null,

    /**
     * 사용자가 현재 이루고 싶은 학습 목표
     *
     * 예:
     * - 스프링으로 CRUD API를 혼자 만들고 싶어요
     * - 엑셀로 월간 매출 데이터를 정리할 수 있게 되고 싶어요
     */
    var goal: String? = null,

    /**
     * 이번 목표에서 만들고 싶은 최종 결과물
     */
    var desiredOutcome: String? = null,

    /**
     * 사용자가 생각하는 현재 실력 수준
     */
    var skillLevel: String? = null,

    /**
     * 최근 직접 해본 작업 또는 경험
     */
    var recentExperience: String? = null,

    /**
     * 사용자가 목표 달성을 원하는 기간
     */
    var targetPeriod: String? = null,

    /**
     * 하루에 투자 가능한 시간
     */
    var dailyAvailableTime: String? = null,

    /**
     * 추가로 남긴 의견
     */
    var additionalOpinion: String? = null,

    /**
     * 추가 질문 필요 여부
     *
     * true:
     *  - 추가 확장 질문이 필요한 상태
     *
     * false:
     *  - 현재 답변만으로 충분하거나 아직 검수 전인 상태
     */
    var needFollowUp: Boolean = false,

    /**
     * 추가 질문 문구
     *
     * 예:
     * - 회사에서 영어가 필요한 구체적인 상황이 있나요?
     */
    var followUpQuestion: String? = null,

    /**
     * 사용자가 추가 질문에 대해 입력한 답변
     */
    var followUpAnswer: String? = null
)
