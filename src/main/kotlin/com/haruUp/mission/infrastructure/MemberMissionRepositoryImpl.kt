package com.haruUp.mission.infrastructure

import com.haruUp.mission.domain.DailyMissionCountDto
import com.haruUp.mission.domain.MemberMissionEntity
import com.haruUp.mission.domain.MissionStatus
import com.haruUp.mission.domain.QMemberMissionEntity
import com.haruUp.mission.domain.QMemberMissionEntity.memberMissionEntity
import com.haruUp.notification.domain.MissionPushTarget
import com.haruUp.notification.domain.QNotificationDeviceToken.notificationDeviceToken
import com.querydsl.core.types.Projections
import com.querydsl.core.types.dsl.BooleanExpression
import com.querydsl.jpa.impl.JPAQueryFactory
import jakarta.persistence.EntityManager
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime

open class MemberMissionRepositoryImpl(
    private val entityManager: EntityManager
) : MemberMissionRepositoryCustom {
    private val queryFactory = JPAQueryFactory(entityManager)

    override fun getTodayMissionsByMemberId(memberId: Long): List<MemberMissionEntity> {
        val subMission = QMemberMissionEntity("subMission")
        val selectedIds = queryFactory
            .select(subMission.id.min())
            .from(subMission)
            .where(
                subMission.memberId.eq(memberId),
                subMission.missionStatus.ne(MissionStatus.COMPLETED),
                subMission.deleted.isFalse,
                subMission.targetDate.eq(LocalDate.now())
            )
            .groupBy(subMission.difficulty)
            .fetch()
            .filterNotNull()

        if (selectedIds.isEmpty()) {
            return emptyList()
        }

        return queryFactory
            .selectFrom(memberMissionEntity)
            .where(memberMissionEntity.id.`in`(selectedIds))
            .orderBy(memberMissionEntity.difficulty.asc())
            .fetch()
    }

    override fun findDifficultiesByMemberIdAndStatus(memberId: Long, status: MissionStatus): List<Int> {
        return queryFactory
            .select(memberMissionEntity.difficulty)
            .from(memberMissionEntity)
            .where(
                memberMissionEntity.memberId.eq(memberId),
                memberMissionEntity.missionStatus.eq(status),
                memberMissionEntity.difficulty.isNotNull
            )
            .fetch()
            .filterNotNull()
    }

    override fun findMissionContentsByMemberIdAndStatus(memberId: Long, status: MissionStatus): List<String> {
        return queryFactory
            .select(memberMissionEntity.missionContent)
            .from(memberMissionEntity)
            .where(
                memberMissionEntity.memberId.eq(memberId),
                memberMissionEntity.missionStatus.eq(status),
                memberMissionEntity.deleted.isFalse
            )
            .fetch()
    }

    @Transactional
    override fun softDeleteByMemberIdAndInterestIdAndStatus(
        memberId: Long,
        memberInterestId: Long,
        status: MissionStatus,
        deletedAt: LocalDateTime
    ): Int {
        return queryFactory
            .update(memberMissionEntity)
            .set(memberMissionEntity.deleted, true)
            .set(memberMissionEntity.deletedAt, deletedAt)
            .where(
                memberMissionEntity.memberId.eq(memberId),
                memberMissionEntity.memberInterestId.eq(memberInterestId),
                memberMissionEntity.missionStatus.eq(status),
                memberMissionEntity.deleted.isFalse
            )
            .execute()
            .toInt()
    }

    @Transactional
    override fun softDeleteByMemberIdAndInterestIdAndStatusExcludingIds(
        memberId: Long,
        memberInterestId: Long,
        status: MissionStatus,
        excludeIds: List<Long>,
        deletedAt: LocalDateTime
    ): Int {
        val predicates = mutableListOf<BooleanExpression>(
            memberMissionEntity.memberId.eq(memberId),
            memberMissionEntity.memberInterestId.eq(memberInterestId),
            memberMissionEntity.missionStatus.eq(status),
            memberMissionEntity.deleted.isFalse
        )

        if (excludeIds.isNotEmpty()) {
            predicates += memberMissionEntity.id.notIn(excludeIds)
        }

        return queryFactory
            .update(memberMissionEntity)
            .set(memberMissionEntity.deleted, true)
            .set(memberMissionEntity.deletedAt, deletedAt)
            .where(*predicates.toTypedArray())
            .execute()
            .toInt()
    }

    override fun findTodayMissions(
        memberId: Long,
        memberInterestId: Long,
        targetDate: LocalDate,
        statuses: List<MissionStatus>
    ): List<MemberMissionEntity> {
        if (statuses.isEmpty()) {
            return emptyList()
        }

        return queryFactory
            .selectFrom(memberMissionEntity)
            .where(
                memberMissionEntity.memberId.eq(memberId),
                memberMissionEntity.memberInterestId.eq(memberInterestId),
                memberMissionEntity.deleted.isFalse,
                memberMissionEntity.targetDate.eq(targetDate),
                memberMissionEntity.missionStatus.`in`(statuses)
            )
            .orderBy(memberMissionEntity.id.asc())
            .fetch()
    }

    override fun findCompletedDatesByMemberIdAndDateRange(
        memberId: Long,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<LocalDate> {
        return queryFactory
            .select(memberMissionEntity.targetDate)
            .distinct()
            .from(memberMissionEntity)
            .where(
                memberMissionEntity.memberId.eq(memberId),
                memberMissionEntity.targetDate.goe(startDate),
                memberMissionEntity.targetDate.loe(endDate),
                memberMissionEntity.missionStatus.eq(MissionStatus.COMPLETED),
                memberMissionEntity.deleted.isFalse
            )
            .fetch()
    }

    @Transactional
    override fun softDeleteAllByMemberId(memberId: Long): Int {
        return queryFactory
            .update(memberMissionEntity)
            .set(memberMissionEntity.deleted, true)
            .set(memberMissionEntity.deletedAt, LocalDateTime.now())
            .where(
                memberMissionEntity.memberId.eq(memberId),
                memberMissionEntity.deleted.isFalse
            )
            .execute()
            .toInt()
    }

    @Transactional
    override fun softDeleteByMemberIdAndInterestId(
        memberId: Long,
        memberInterestId: Long,
        deletedAt: LocalDateTime
    ): Int {
        return queryFactory
            .update(memberMissionEntity)
            .set(memberMissionEntity.deleted, true)
            .set(memberMissionEntity.deletedAt, deletedAt)
            .where(
                memberMissionEntity.memberId.eq(memberId),
                memberMissionEntity.memberInterestId.eq(memberInterestId),
                memberMissionEntity.deleted.isFalse
            )
            .execute()
            .toInt()
    }

    override fun findSelectedMissionsByTargetDate(targetDate: LocalDate): List<MemberMissionEntity> {
        return queryFactory
            .selectFrom(memberMissionEntity)
            .where(
                memberMissionEntity.isSelected.isTrue,
                memberMissionEntity.targetDate.eq(targetDate),
                memberMissionEntity.deleted.isFalse
            )
            .fetch()
    }

    @Transactional
    override fun updateLabelNameAndEmbedding(
        id: Long,
        labelName: String,
        embedding: String,
        updatedAt: LocalDateTime
    ): Int {
        val sql = """
            UPDATE member_mission
            SET label_name = :labelName,
                embedding = CAST(:embedding AS vector),
                updated_at = :updatedAt
            WHERE id = :id
        """.trimIndent()

        return entityManager
            .createNativeQuery(sql)
            .setParameter("labelName", labelName)
            .setParameter("embedding", embedding)
            .setParameter("updatedAt", updatedAt)
            .setParameter("id", id)
            .executeUpdate()
    }

    @Transactional
    override fun updateLabelName(
        id: Long,
        labelName: String,
        updatedAt: LocalDateTime
    ): Int {
        return queryFactory
            .update(memberMissionEntity)
            .set(memberMissionEntity.labelName, labelName)
            .set(memberMissionEntity.updatedAt, updatedAt)
            .where(memberMissionEntity.id.eq(id))
            .execute()
            .toInt()
    }

    override fun findSimilarMission(embedding: String, threshold: Double): MemberMissionEntity? {
        val sql = """
            SELECT * FROM member_mission
            WHERE embedding IS NOT NULL
              AND label_name IS NOT NULL
              AND deleted = false
              AND (embedding <=> CAST(:embedding AS vector)) < :threshold
            ORDER BY embedding <=> CAST(:embedding AS vector)
            LIMIT 1
        """.trimIndent()

        @Suppress("UNCHECKED_CAST")
        val result = entityManager
            .createNativeQuery(sql, MemberMissionEntity::class.java)
            .setParameter("embedding", embedding)
            .setParameter("threshold", threshold)
            .resultList as List<MemberMissionEntity>

        return result.firstOrNull()
    }

    override fun findSelectedMissionsWithoutLabel(targetDate: LocalDate): List<MemberMissionEntity> {
        return queryFactory
            .selectFrom(memberMissionEntity)
            .where(
                memberMissionEntity.isSelected.isTrue,
                memberMissionEntity.targetDate.eq(targetDate),
                memberMissionEntity.labelName.isNull,
                memberMissionEntity.deleted.isFalse
            )
            .fetch()
    }

    override fun findDailyCompletedMissionCount(
        memberId: Long,
        targetStartDate: LocalDate,
        targetEndDate: LocalDate
    ): List<DailyMissionCountDto> {
        return queryFactory
            .select(
                Projections.constructor(
                    DailyMissionCountDto::class.java,
                    memberMissionEntity.targetDate,
                    memberMissionEntity.id.count()
                )
            )
            .from(memberMissionEntity)
            .where(
                memberMissionEntity.memberId.eq(memberId),
                memberMissionEntity.missionStatus.eq(MissionStatus.COMPLETED),
                memberMissionEntity.deleted.isFalse,
                memberMissionEntity.targetDate.goe(targetStartDate),
                memberMissionEntity.targetDate.loe(targetEndDate)
            )
            .groupBy(memberMissionEntity.targetDate)
            .orderBy(memberMissionEntity.targetDate.asc())
            .fetch()
    }

    override fun countTodaySelectedMissions(
        memberId: Long,
        targetDate: LocalDate,
        statuses: List<MissionStatus>
    ): Long {
        if (statuses.isEmpty()) {
            return 0L
        }

        return queryFactory
            .select(memberMissionEntity.count())
            .from(memberMissionEntity)
            .where(
                memberMissionEntity.memberId.eq(memberId),
                memberMissionEntity.targetDate.eq(targetDate),
                memberMissionEntity.deleted.isFalse,
                memberMissionEntity.missionStatus.`in`(statuses)
            )
            .fetchOne() ?: 0L
    }

    override fun findMembersWithTodayFalseMission(
        atStartOfDay: LocalDateTime,
        atEndOfDay: LocalDateTime
    ): List<MissionPushTarget> {
        return queryFactory
            .select(
                Projections.constructor(
                    MissionPushTarget::class.java,
                    memberMissionEntity.memberId,
                    notificationDeviceToken.deviceId
                )
            )
            .distinct()
            .from(memberMissionEntity)
            .join(notificationDeviceToken)
            .on(memberMissionEntity.memberId.eq(notificationDeviceToken.memberId))
            .where(
                memberMissionEntity.missionStatus.eq(MissionStatus.INACTIVE),
                memberMissionEntity.deleted.isFalse,
                memberMissionEntity.createdAt.goe(atStartOfDay),
                memberMissionEntity.createdAt.lt(atEndOfDay)
            )
            .fetch()
    }
}
