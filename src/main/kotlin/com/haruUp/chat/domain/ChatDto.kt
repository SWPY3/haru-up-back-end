package com.haruUp.chat.domain

import org.hibernate.type.internal.ImmutableNamedBasicTypeImpl

data class ChatDto(
     // 대화 단계
     var depth: Int = 0,

     // 사용자 메시지 또는 챗봇 답변
     var content: String = "",

     // 메시지 역할 (USER 또는 BOT)
     var role: String = "",

     // 이전 대화 내용
     var previousMessages: List<ChatDto> = emptyList()
)