package com.haruUp.chat.domain

import lombok.Data

@Data
data class ChatResponse (

    /**
     * 챗봇 응답 DTO
     *
     * 서버가 프론트에 반환하는 응답 객체이다.
     *
     * @property message
     *  - 사용자에게 보여줄 챗봇 메시지
     *
     * @property nextDepth
     *  - 다음 대화 단계
     *  - 프론트에서 참고용으로 사용할 수 있다.
     *
     * @property completed
     *  - true면 현재 대화가 종료되었음을 의미한다.
     *  - false면 다음 입력을 계속 받아야 한다.
     *
     * @property options
     *  - 선택형 질문일 경우 프론트에 표시할 선택지 목록
     *  - 자유 입력 단계면 빈 리스트로 반환한다.
     */

    val message : String = "",
    val nextDepth : Int = 0,
    val completed : Boolean = false,
    val options : List<ChatOption> = emptyList()
)
