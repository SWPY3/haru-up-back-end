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
     * 3 = 학습 동기 입력 단계
     * 4 = 추가 질문 입력 단계
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
     * 사용자가 입력한 학습 동기
     *
     * 예:
     * - 회사에서 영어 메일을 자주 써야 해서요
     * - 취업 준비 때문에 필요해요
     */
    var motivation: String? = null,

    /**
     * 추가 질문 필요 여부
     *
     * true:
     *  - 동기 답변이 충분하지 않아 추가 질문이 필요한 상태
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