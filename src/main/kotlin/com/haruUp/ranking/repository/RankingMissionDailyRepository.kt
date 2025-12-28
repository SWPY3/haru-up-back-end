package com.haruUp.ranking.repository

import com.haruUp.ranking.domain.RankingMissionDailyEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface RankingMissionDailyRepository : JpaRepository<RankingMissionDailyEntity, Long> {

    fun existsByMemberMissionId(memberMissionId: Long): Boolean

    fun findAllByMemberMissionIdIn(memberMissionIds: List<Long>): List<RankingMissionDailyEntity>

    /**
     * 인기차트 조회 (필터링 + 그룹핑)
     * 네이티브 쿼리 사용 (PostgreSQL 배열 인덱스 접근)
     */
    @Query(
        value = """
            SELECT
                label_name as labelName,
                interest_full_path[1] as interestCategory,
                COUNT(*) as selectionCount
            FROM ranking_mission_daily
            WHERE label_name IS NOT NULL
              AND deleted = false
              AND (:gender IS NULL OR gender = :gender)
              AND (:ageGroup IS NULL OR age_group = :ageGroup)
              AND (:jobId IS NULL OR job_id = :jobId)
              AND (:jobDetailId IS NULL OR job_detail_id = :jobDetailId)
              AND (:interest IS NULL OR interest_full_path[1] = :interest)
            GROUP BY label_name, interest_full_path[1]
            ORDER BY COUNT(*) DESC
        """,
        nativeQuery = true
    )
    fun findPopularMissions(
        @Param("gender") gender: String?,
        @Param("ageGroup") ageGroup: String?,
        @Param("jobId") jobId: Long?,
        @Param("jobDetailId") jobDetailId: Long?,
        @Param("interest") interest: String?
    ): List<RankingProjection>
}

interface RankingProjection {
    fun getLabelName(): String?
    fun getInterestCategory(): String?
    fun getSelectionCount(): Long
}
