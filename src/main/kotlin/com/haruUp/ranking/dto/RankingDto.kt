package com.haruUp.ranking.dto

import com.haruUp.member.domain.type.MemberGender
import com.haruUp.ranking.domain.AgeGroup

/**
 * 인기차트 조회 요청
 */
data class RankingFilterRequest(
    val gender: MemberGender? = null,
    val ageGroup: AgeGroup? = null,
    val jobId: Long? = null,
    val jobDetailId: Long? = null,
    val interest: String? = null
)

/**
 * 인기차트 조회 응답
 */
data class PopularMissionResponse(
    val rank: Int,
    val labelName: String,
    val interestCategory: String?,
    val selectionCount: Long,
    val missions: List<MissionItem> = emptyList()
)

data class MissionItem(
    val missionId: Long,
    val missionContent: String,
    val difficulty: Int?
)

/**
 * 배치 실행 결과
 */
data class RankingBatchResult(
    val processedCount: Int,
    val newLabelCount: Int,
    val existingLabelCount: Int,
    val skippedCount: Int,
    val errors: List<String> = emptyList()
)

/**
 * 배치 대상 데이터 (조인 결과)
 */
data class RankingBatchTarget(
    val memberMissionId: Long,
    val missionId: Long,
    val memberId: Long,
    val memberInterestId: Long,
    val missionContent: String?,
    val missionEmbedding: String?,
    val existingLabelName: String?,
    val interestFullPath: List<String>?,
    val gender: MemberGender?,
    val birthDt: java.time.LocalDateTime?,
    val jobId: Long?,
    val jobDetailId: Long?
)
