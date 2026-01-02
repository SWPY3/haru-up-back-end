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
     * 인기차트 조회 (필터링 + 그룹핑) - 다중 선택 지원
     * 네이티브 쿼리 사용 (PostgreSQL 배열 인덱스 접근)
     * birth_dt로 나이 계산하여 연령대 필터링
     *
     * label_name은 ranking_mission_daily에 비정규화되어 있음 (조인 불필요)
     */
    @Query(
        value = """
            SELECT
                rmd.label_name as labelName,
                rmd.interest_full_path as interestFullPath,
                COUNT(*) as selectionCount
            FROM ranking_mission_daily rmd
            WHERE rmd.label_name IS NOT NULL
              AND rmd.deleted = false
              AND rmd.ranking_date >= CURRENT_DATE - INTERVAL '30 days'
              AND (:gender IS NULL OR rmd.gender = :gender)
              AND (:ages IS NULL OR EXTRACT(YEAR FROM AGE(CURRENT_DATE, rmd.birth_dt))::int = ANY(CAST(string_to_array(:ages, ',') AS int[])))
              AND (:jobIds IS NULL OR rmd.job_id = ANY(CAST(string_to_array(:jobIds, ',') AS bigint[])))
              AND (:jobDetailIds IS NULL OR rmd.job_detail_id = ANY(CAST(string_to_array(:jobDetailIds, ',') AS bigint[])))
              AND (:interests IS NULL OR rmd.interest_full_path[1] = ANY(string_to_array(:interests, ',')))
            GROUP BY rmd.label_name, rmd.interest_full_path
            ORDER BY COUNT(*) DESC
        """,
        nativeQuery = true
    )
    fun findPopularMissions(
        @Param("gender") gender: String?,
        @Param("ages") ages: String?,
        @Param("jobIds") jobIds: String?,
        @Param("jobDetailIds") jobDetailIds: String?,
        @Param("interests") interests: String?
    ): List<RankingProjection>
}

interface RankingProjection {
    fun getLabelName(): String?
    fun getInterestFullPath(): Any?  // PostgreSQL TEXT[] -> Object (변환 필요)
    fun getSelectionCount(): Long
}
