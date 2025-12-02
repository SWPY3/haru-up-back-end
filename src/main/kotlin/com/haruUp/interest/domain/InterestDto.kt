package com.haruUp.interest.domain

data class InterestDto(
    val id: Long? = null,
    val parentId: Long? = null,
    val depth: Int,
    val interestName: String,
    val normalizedKey: String? = null,
    val createdSource: CreatedSourceType
)
