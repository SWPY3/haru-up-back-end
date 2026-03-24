package com.haruUp.chat.domain

import lombok.Data

@Data
data class ChatRequest (
     /**
      * 챗봇 요청 DTO
      *
      * 프론트에서 챗봇 API를 호출할 때 전달하는 값이다.
      *
      * @property sessionId
      *  - 사용자의 현재 대화를 식별하기 위한 고유 값
      *  - Redis에 저장된 ChatState를 조회할 때 key로 사용된다.
      *
      * @property content
      *  - 사용자가 현재 입력한 답변
      *  - 예: "외국어 공부", "영어", "회사에서 필요해서요"
      */

     val sessionId : String = "",
     val content : String = ""
)