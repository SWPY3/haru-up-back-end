package com.haruUp.member.infrastructure

import com.haruUp.character.domain.QCharacter.character
import com.haruUp.character.domain.QLevel.level
import com.haruUp.character.domain.QMemberCharacter.memberCharacter
import com.haruUp.member.domain.QMember.member
import com.haruUp.member.domain.QMemberProfile.memberProfile
import com.haruUp.member.domain.QMemberSetting.memberSetting
import com.haruUp.member.domain.dto.HomeMemberInfoDto
import com.haruUp.member.domain.dto.MemberStatisticsDto
import com.querydsl.core.types.Projections
import com.querydsl.jpa.impl.JPAQueryFactory
import jakarta.persistence.EntityManager
import java.time.LocalDate

class MemberRepositoryImpl(
    private val entityManager: EntityManager
) : MemberRepositoryCustom {
    private val queryFactory = JPAQueryFactory(entityManager)

    override fun homeMemberInfo(memberId: Long): List<HomeMemberInfoDto> {
        return queryFactory
            .select(
                Projections.constructor(
                    HomeMemberInfoDto::class.java,
                    character.id,
                    memberCharacter.totalExp.longValue(),
                    memberCharacter.currentExp.longValue(),
                    level.maxExp,
                    level.levelNumber,
                    memberProfile.nickname
                )
            )
            .from(member)
            .join(memberSetting).on(memberSetting.memberId.eq(member.id))
            .join(memberCharacter).on(memberCharacter.memberId.eq(member.id))
            .join(character).on(character.id.eq(memberCharacter.characterId))
            .join(level).on(level.id.eq(memberCharacter.levelId))
            .join(memberProfile).on(memberProfile.memberId.eq(member.id))
            .where(member.id.eq(memberId))
            .fetch()
    }

    override fun memberStatisticsList(): List<MemberStatisticsDto> {
        return queryFactory
            .select(
                Projections.constructor(
                    MemberStatisticsDto::class.java,
                    member.snsId,
                    member.name,
                    level.levelNumber,
                    character.id,
                    member.createdAt
                )
            )
            .from(member)
            .join(memberCharacter).on(memberCharacter.memberId.eq(member.id))
            .join(character).on(character.id.eq(memberCharacter.characterId))
            .join(level).on(level.id.eq(memberCharacter.levelId))
            .where(
                member.name.ne("string"),
                member.deleted.isFalse,
                member.createdAt.goe(MEMBER_STATISTICS_START_AT)
            )
            .orderBy(member.createdAt.desc())
            .fetch()
    }

    companion object {
        private val MEMBER_STATISTICS_START_AT = LocalDate.of(2026, 1, 7).atStartOfDay()
    }
}
