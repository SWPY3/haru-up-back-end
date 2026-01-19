package com.haruUp.member.infrastructure

import com.haruUp.member.controller.MemberAccountController
import com.haruUp.member.domain.type.LoginType
import com.haruUp.member.domain.Member
import com.haruUp.member.domain.dto.HomeMemberInfoDto
import com.haruUp.member.domain.dto.MemberStatisticsDto
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param


interface MemberRepository : JpaRepository<Member, Long> {

    fun removeMemberById(id: Long)

    fun findByEmailAndPasswordAndDeletedFalse(email: String?, password: String?) : Member?

    fun findByEmailAndLoginTypeAndDeletedFalse(email: String, common: LoginType): Member?

    fun findByLoginTypeAndSnsIdAndDeletedFalse(loginType: LoginType?, snsId: String): Member?


    @Query(
        """
    SELECT new com.haruUp.member.domain.dto.HomeMemberInfoDto(
        cr.id,
        mc.totalExp,
        mc.currentExp,
        lv.maxExp,
        lv.levelNumber,
        mp.nickname
    )
    FROM Member m
    JOIN MemberSetting ms ON ms.memberId = m.id
    JOIN MemberCharacter mc ON mc.memberId = m.id
    JOIN Character cr ON cr.id = mc.characterId
    JOIN Level lv ON mc.levelId = lv.id
    JOIN MemberProfile mp ON mp.memberId = m.id
    WHERE m.id = :memberId
    """
    )
    fun homeMemberInfo(
        @Param("memberId") memberId: Long
    ): List<HomeMemberInfoDto>

    @Query(
        value = """
        select
            t.sns_id      as snsId,
            t.name        as name,
            t.level_number as levelNumber,
            t.character_id as characterId,
            t.created_at  as createdAt
        from (
            select
                m.sns_id,
                m.name,
                l.level_number,
                c.id as character_id,
                m.created_at,
                row_number() over (
                    partition by m.sns_id
                    order by m.created_at desc
                ) as rn
            from member m
                     inner join member_character mc on m.id = mc.member_id
                     inner join character c on mc.character_id = c.id
                     inner join level l on mc.level_id = l.id
            where m.name is not null
              and m.name <> ''
              and m.name <> 'string'
        ) t
        where t.rn = 1
        order by t.created_at desc
    """,
        nativeQuery = true
    )
    fun memberStatisticsList(): List<MemberStatisticsDto>
}