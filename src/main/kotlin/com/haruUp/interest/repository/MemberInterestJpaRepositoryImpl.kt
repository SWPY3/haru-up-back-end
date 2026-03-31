package com.haruUp.interest.repository

import com.haruUp.interest.entity.QMemberInterestEntity.memberInterestEntity
import com.querydsl.jpa.impl.JPAQueryFactory
import jakarta.persistence.EntityManager
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

open class MemberInterestJpaRepositoryImpl(
    private val entityManager: EntityManager
) : MemberInterestJpaRepositoryCustom {
    private val queryFactory = JPAQueryFactory(entityManager)

    @Transactional
    override fun softDeleteAllByMemberId(memberId: Long): Int {
        return queryFactory
            .update(memberInterestEntity)
            .set(memberInterestEntity.deleted, true)
            .set(memberInterestEntity.deletedAt, LocalDateTime.now())
            .where(memberInterestEntity.memberId.eq(memberId))
            .execute()
            .toInt()
    }

    @Transactional
    override fun softDeleteByIdAndMemberId(id: Long, memberId: Long): Int {
        return queryFactory
            .update(memberInterestEntity)
            .set(memberInterestEntity.deleted, true)
            .set(memberInterestEntity.deletedAt, LocalDateTime.now())
            .where(
                memberInterestEntity.id.eq(id),
                memberInterestEntity.memberId.eq(memberId)
            )
            .execute()
            .toInt()
    }
}
