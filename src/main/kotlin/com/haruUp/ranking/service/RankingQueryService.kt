package com.haruUp.ranking.service

import com.haruUp.ranking.dto.PopularMissionResponse
import com.haruUp.ranking.dto.RankingFilterRequest
import com.haruUp.ranking.repository.RankingMissionDailyRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class RankingQueryService(
    private val rankingMissionDailyRepository: RankingMissionDailyRepository
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 인기차트 조회 (다중 선택 지원)
     * 필터 조건에 따라 라벨별 선택 횟수를 집계하여 반환
     */
    fun getPopularMissions(
        filter: RankingFilterRequest,
        limit: Int = 10
    ): List<PopularMissionResponse> {
        logger.info("인기차트 조회 - filter: $filter, limit: $limit")

        // 연령대 -> 나이 배열로 변환 후 콤마 구분 문자열로 (예: EARLY_20S -> "20,21,22")
        val ages = filter.ageGroups?.flatMap { ageGroup ->
            (ageGroup.minAge..ageGroup.maxAge).toList()
        }?.joinToString(",")

        val rankings = rankingMissionDailyRepository.findPopularMissions(
            gender = filter.gender?.name,
            ages = ages,
            jobIds = filter.jobIds?.joinToString(","),
            jobDetailIds = filter.jobDetailIds?.joinToString(","),
            interests = filter.interests?.joinToString(",")
        ).take(limit)

        return rankings.mapIndexed { index, projection ->
            val labelName = projection.getLabelName() ?: return@mapIndexed null

            // PostgreSQL TEXT[] -> List<String> 변환
            val interestFullPath = convertToStringList(projection.getInterestFullPath())

            PopularMissionResponse(
                rank = index + 1,
                labelName = labelName,
                interestFullPath = interestFullPath,
                selectionCount = projection.getSelectionCount()
            )
        }.filterNotNull()
    }

    /**
     * PostgreSQL TEXT[] 결과를 List<String>으로 변환
     */
    private fun convertToStringList(value: Any?): List<String>? {
        return when (value) {
            null -> null
            is Array<*> -> value.filterIsInstance<String>()
            is java.sql.Array -> (value.array as? Array<*>)?.filterIsInstance<String>()
            else -> null
        }
    }
}
