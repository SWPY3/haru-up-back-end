package com.haruUp.mission.infrastructure

import com.haruUp.mission.domain.DailyMissionCountDto
import com.haruUp.mission.domain.MemberMissionEntity
import com.haruUp.mission.domain.MissionStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime

interface MemberMissionRepository : JpaRepository<MemberMissionEntity, Long> {

    /** * 사용자의 모든 미션 조회 */
    fun findByMemberId(memberId: Long): List<MemberMissionEntity>

    /** * 사용자의 삭제되지 않은 미션 조회 */
    fun findByMemberIdAndDeletedFalse(memberId: Long): List<MemberMissionEntity>

    /** * ID로 삭제되지 않은 미션 조회 */
    fun findByIdAndDeletedFalse(id: Long): MemberMissionEntity?

    /* 오늘의 추천 미션 조회 - member_mission.difficulty 기준 */
    @Query(
        value = """
            SELECT mm.*
            FROM member_mission mm
            WHERE mm.id IN (
                SELECT id FROM (
                    SELECT
                        m.id,
                        ROW_NUMBER() OVER (
                            PARTITION BY m.difficulty
                            ORDER BY
                                CASE WHEN m.target_date = CURRENT_DATE THEN 0 ELSE 1 END
                        ) AS rn
                    FROM member_mission m
                    WHERE m.member_id = :memberId
                      AND m.mission_status <> 'COMPLETED'
                      AND m.deleted = false
                      AND m.target_date = CURRENT_DATE
                ) sub
                WHERE sub.rn = 1
            )
            ORDER BY mm.difficulty
        """,
        nativeQuery = true
    )
    fun getTodayMissionsByMemberId(memberId: Long): List<MemberMissionEntity>

    /**
     * 사용자의 특정 상태 미션 난이도 목록 조회
     * 오늘의 미션 추천 시 제외할 난이도 조회에 사용
     */
    @Query("""
    SELECT m.difficulty
    FROM MemberMissionEntity m
    WHERE m.memberId = :memberId
      AND m.missionStatus = :status
      AND m.difficulty IS NOT NULL
    """)
    fun findDifficultiesByMemberIdAndStatus(
        memberId: Long,
        status: MissionStatus
    ): List<Int>

    /**
     * 사용자의 특정 상태 미션 내용 목록 조회
     * 오늘의 미션 추천 시 제외할 미션 내용 조회에 사용
     */
    @Query("""
    SELECT m.missionContent
    FROM MemberMissionEntity m
    WHERE m.memberId = :memberId
      AND m.missionStatus = :status
      AND m.deleted = false
    """)
    fun findMissionContentsByMemberIdAndStatus(
        memberId: Long,
        status: MissionStatus
    ): List<String>

    /**
     * 특정 관심사의 READY 상태 미션을 soft delete 처리
     * 새로운 미션 추천 시 해당 관심사의 기존 READY 상태 미션들만 삭제 처리
     */
    @Transactional
    @Modifying
    @Query("""
    UPDATE MemberMissionEntity m
    SET m.deleted = true, m.deletedAt = :deletedAt
    WHERE m.memberId = :memberId
      AND m.memberInterestId = :memberInterestId
      AND m.missionStatus = :status
      AND m.deleted = false
    """)
    fun softDeleteByMemberIdAndInterestIdAndStatus(
        memberId: Long,
        memberInterestId: Long,
        status: MissionStatus,
        deletedAt: LocalDateTime
    ): Int

    /**
     * 특정 ID를 제외하고 soft delete
     */
    @Transactional
    @Modifying
    @Query("""
    UPDATE MemberMissionEntity m
    SET m.deleted = true, m.deletedAt = :deletedAt
    WHERE m.memberId = :memberId
      AND m.memberInterestId = :memberInterestId
      AND m.missionStatus = :status
      AND m.deleted = false
      AND m.id NOT IN :excludeIds
    """)
    fun softDeleteByMemberIdAndInterestIdAndStatusExcludingIds(
        memberId: Long,
        memberInterestId: Long,
        status: MissionStatus,
        excludeIds: List<Long>,
        deletedAt: LocalDateTime
    ): Int

    /**
     * 오늘의 미션 조회
     * - deleted = false
     * - targetDate = 오늘
     * - missionStatus IN (READY, ACTIVE)
     * - 특정 memberInterestId
     */
    @Query("""
    SELECT m FROM MemberMissionEntity m
    WHERE m.memberId = :memberId
      AND m.memberInterestId = :memberInterestId
      AND m.deleted = false
      AND m.targetDate = :targetDate
      AND m.missionStatus IN :statuses
    ORDER BY m.id
    """)
    fun findTodayMissions(
        memberId: Long,
        memberInterestId: Long,
        targetDate: LocalDate,
        statuses: List<MissionStatus>
    ): List<MemberMissionEntity>

    /**
     * memberId, memberInterestId로 삭제되지 않은 미션 조회
     * 제외할 미션 유효성 검증에 사용
     */
    fun findByMemberIdAndMemberInterestIdAndDeletedFalse(
        memberId: Long,
        memberInterestId: Long
    ): List<MemberMissionEntity>

    /**
     * memberId, memberInterestId로 삭제되지 않은 READY 상태 미션만 조회
     * 재추천 시 제외할 미션 유효성 검증에 사용
     */
    fun findByMemberIdAndMemberInterestIdAndMissionStatusAndDeletedFalse(
        memberId: Long,
        memberInterestId: Long,
        missionStatus: MissionStatus
    ): List<MemberMissionEntity>

    /**
     * 특정 날짜에 추천된 미션 조회 (제외할 미션 조회용)
     */
    fun findByMemberIdAndMemberInterestIdAndTargetDate(
        memberId: Long,
        memberInterestId: Long,
        targetDate: LocalDate
    ): List<MemberMissionEntity>

    /**
     * 날짜 범위 내에서 COMPLETED 상태인 미션의 targetDate 목록 조회
     * - 중복 제거 (DISTINCT)
     */
    @Query("""
    SELECT DISTINCT m.targetDate FROM MemberMissionEntity m
    WHERE m.memberId = :memberId
      AND m.targetDate >= :startDate
      AND m.targetDate <= :endDate
      AND m.missionStatus = 'COMPLETED'
      AND m.deleted = false
    """)
    fun findCompletedDatesByMemberIdAndDateRange(
        memberId: Long,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<LocalDate>

    /**
     * 특정 사용자의 모든 미션 soft delete
     */
    @Transactional
    @Modifying
    @Query("""
    UPDATE MemberMissionEntity m
    SET m.deleted = true, m.deletedAt = CURRENT_TIMESTAMP
    WHERE m.memberId = :memberId
      AND m.deleted = false
    """)
    fun softDeleteAllByMemberId(memberId: Long): Int

    /**
     * 특정 사용자의 특정 관심사에 해당하는 모든 미션 soft delete
     * (상태 무관)
     */
    @Transactional
    @Modifying
    @Query("""
    UPDATE MemberMissionEntity m
    SET m.deleted = true, m.deletedAt = :deletedAt
    WHERE m.memberId = :memberId
      AND m.memberInterestId = :memberInterestId
      AND m.deleted = false
    """)
    fun softDeleteByMemberIdAndInterestId(
        memberId: Long,
        memberInterestId: Long,
        deletedAt: LocalDateTime
    ): Int

    /**
     * 선택된 미션 조회 (랭킹 배치용)
     * - is_selected = true
     * - target_date = 지정된 날짜
     * - deleted = false
     */
    @Query("""
    SELECT m FROM MemberMissionEntity m
    WHERE m.isSelected = true
      AND m.targetDate = :targetDate
      AND m.deleted = false
    """)
    fun findSelectedMissionsByTargetDate(targetDate: LocalDate): List<MemberMissionEntity>

    /**
     * 라벨명과 임베딩 업데이트 (랭킹 배치용)
     * Native Query로 vector 타입 처리
     */
    @Transactional
    @Modifying
    @Query(
        value = """
            UPDATE member_mission
            SET label_name = :labelName,
                embedding = CAST(:embedding AS vector),
                updated_at = :updatedAt
            WHERE id = :id
        """,
        nativeQuery = true
    )
    fun updateLabelNameAndEmbedding(
        id: Long,
        labelName: String,
        embedding: String,
        updatedAt: LocalDateTime
    ): Int

    /**
     * 라벨명만 업데이트 (임베딩 없이)
     */
    @Transactional
    @Modifying
    @Query("""
    UPDATE MemberMissionEntity m
    SET m.labelName = :labelName, m.updatedAt = :updatedAt
    WHERE m.id = :id
    """)
    fun updateLabelName(
        id: Long,
        labelName: String,
        updatedAt: LocalDateTime
    ): Int

    /**
     * 유사 미션 검색 (코사인 유사도)
     * threshold 이하의 거리를 가진 미션 중 가장 유사한 것 반환
     *
     * pgvector 연산자: <=> (코사인 거리, 0에 가까울수록 유사)
     */
    @Query(
        value = """
            SELECT * FROM member_mission
            WHERE embedding IS NOT NULL
              AND label_name IS NOT NULL
              AND deleted = false
              AND (embedding <=> CAST(:embedding AS vector)) < :threshold
            ORDER BY embedding <=> CAST(:embedding AS vector)
            LIMIT 1
        """,
        nativeQuery = true
    )
    fun findSimilarMission(
        embedding: String,
        threshold: Double
    ): MemberMissionEntity?

    /**
     * 라벨이 없는 선택된 미션 조회 (배치용)
     */
    @Query("""
    SELECT m FROM MemberMissionEntity m
    WHERE m.isSelected = true
      AND m.targetDate = :targetDate
      AND m.labelName IS NULL
      AND m.deleted = false
    """)
    fun findSelectedMissionsWithoutLabel(targetDate: LocalDate): List<MemberMissionEntity>

    @Query("""
        SELECT
            m.targetDate AS targetDate,
            COUNT(m.id) AS completedCount
        FROM MemberMissionEntity m
        WHERE m.memberId = :memberId
          AND m.missionStatus = 'COMPLETED'
          AND m.deleted = false
          AND m.targetDate >= :targetStartDate
          AND m.targetDate <= :targetEndDate
        GROUP BY m.targetDate
        ORDER BY m.targetDate
    """)
    fun findDailyCompletedMissionCount(
        memberId: Long,
        targetStartDate: LocalDate,
        targetEndDate: LocalDate
    ): List<DailyMissionCountDto>

    /**
     * 오늘 선택된 미션 개수 조회 (하루 최대 5개 제한용)
     * - targetDate = 오늘
     * - deleted = false
     * - missionStatus IN (COMPLETED, ACTIVE, INACTIVE)
     */
    @Query("""
    SELECT COUNT(m) FROM MemberMissionEntity m
    WHERE m.memberId = :memberId
      AND m.targetDate = :targetDate
      AND m.deleted = false
      AND m.missionStatus IN :statuses
    """)
    fun countTodaySelectedMissions(
        memberId: Long,
        targetDate: LocalDate,
        statuses: List<MissionStatus>
    ): Long

}