package com.haruUp.ranking.dto

import com.haruUp.member.domain.type.MemberGender
import com.haruUp.ranking.domain.AgeGroup

/**
 * 인기차트 조회 요청 (다중 선택 지원)
 */
data class RankingFilterRequest(
    val gender: MemberGender? = null,
    val ageGroups: List<AgeGroup>? = null,
    val jobIds: List<Long>? = null,
    val jobDetailIds: List<Long>? = null,
    val interests: List<String>? = null
)

/**
 * 인기차트 조회 응답
 */
data class PopularMissionResponse(
    val rank: Int,
    val labelName: String,
    val interestFullPath: List<String>?,
    val selectionCount: Long
)


