package com.haruUp.chat.domain

import org.hibernate.type.internal.ImmutableNamedBasicTypeImpl

data class ChatDto(
     var depth: Int = 0,
     var content: String = "",
     var role: String = ""
)