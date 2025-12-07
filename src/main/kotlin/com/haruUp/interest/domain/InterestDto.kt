package com.haruUp.interest.domain

data class InterestDto(
    val id: Long? = null,
    val parentId: Long? = null,
    val level: String,  // "MAIN", "MIDDLE", "SUB"
    val name: String,
    val createdSource: CreatedSourceType
)
