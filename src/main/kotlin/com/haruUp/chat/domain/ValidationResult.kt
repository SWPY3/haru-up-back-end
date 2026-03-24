package com.haruUp.chat.domain

import lombok.Data

@Data
data class ValidationResult (

    /**
     * LLM 검수 결과 객체
     *
     * 사용자의 동기 답변이 충분한지 여부와,
     * 추가 질문이 필요할 경우 어떤 질문을 던질지 담는다.
     *
     * @property isValid
     *  - true: 현재 답변만으로도 충분함
     *  - false: 추가 질문이 필요함
     *
     * @property reason
     *  - LLM이 판단한 이유
     *  - 현재는 로그/디버깅 용도로 활용 가능
     *
     * @property followUpQuestion
     *  - 답변이 부족할 경우 사용자에게 던질 추가 질문
     */
    var isValid: Boolean = false,
    var reason: String? = null,
    var followUpQuestion : String? = null
)