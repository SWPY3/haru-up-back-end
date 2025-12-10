package com.haruUp.interest.dto

import com.haruUp.interest.entity.CreatedSourceType

data class InterestEntityDto(
    val id: Long? = null,
    val parentId: Long? = null,
    val level: String,  // "MAIN", "MIDDLE", "SUB"
    val name: String,
    val createdSource: CreatedSourceType
)
