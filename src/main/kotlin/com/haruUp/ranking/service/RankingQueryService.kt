package com.haruUp.ranking.service

import com.haruUp.missionembedding.repository.MissionEmbeddingRepository
import com.haruUp.ranking.dto.MissionItem
import com.haruUp.ranking.dto.PopularMissionResponse
import com.haruUp.ranking.dto.RankingFilterRequest
import com.haruUp.ranking.repository.RankingMissionDailyRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class RankingQueryService(
    private val rankingMissionDailyRepository: RankingMissionDailyRepository,
    private val missionEmbeddingRepository: MissionEmbeddingRepository
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 인기차트 조회
     * 필터 조건에 따라 라벨별 선택 횟수를 집계하여 반환
     */
    fun getPopularMissions(
        filter: RankingFilterRequest,
        limit: Int = 10
    ): List<PopularMissionResponse> {
        logger.info("인기차트 조회 - filter: $filter, limit: $limit")

        val rankings = rankingMissionDailyRepository.findPopularMissions(
            gender = filter.gender?.name,
            ageGroup = filter.ageGroup?.name,
            jobId = filter.jobId,
            jobDetailId = filter.jobDetailId,
            interest = filter.interest
        ).take(limit)

        return rankings.mapIndexed { index, projection ->
            val labelName = projection.getLabelName() ?: return@mapIndexed null

            // 해당 라벨의 미션 목록 조회 (옵션)
            val missions = getMissionsByLabel(labelName)

            PopularMissionResponse(
                rank = index + 1,
                labelName = labelName,
                interestCategory = projection.getInterestCategory(),
                selectionCount = projection.getSelectionCount(),
                missions = missions
            )
        }.filterNotNull()
    }

    /**
     * 특정 라벨에 해당하는 미션 목록 조회
     */
    private fun getMissionsByLabel(labelName: String): List<MissionItem> {
        // mission_embedding에서 해당 라벨의 미션들 조회
        val missions = missionEmbeddingRepository.findAll()
            .filter { it.labelName == labelName }
            .sortedByDescending { it.usageCount }
            .take(5)

        return missions.map {
            MissionItem(
                missionId = it.id!!,
                missionContent = it.missionContent,
                difficulty = it.difficulty
            )
        }
    }
}
