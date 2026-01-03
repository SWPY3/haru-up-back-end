package com.haruUp.member.infrastructure

import com.haruUp.member.controller.MemberAccountController
import com.haruUp.member.domain.type.LoginType
import com.haruUp.member.domain.Member
import com.haruUp.member.domain.dto.HomeMemberInfoDto
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
        mc.totalExp,
        mc.currentExp,
        lv.maxExp,
        lv.levelNumber,
        mp.nickname
    )
    FROM Member m
    JOIN MemberSetting ms ON ms.memberId = m.id
    JOIN MemberCharacter mc ON mc.memberId = m.id
    JOIN Level lv ON mc.levelId = lv.id
    JOIN MemberProfile mp ON mp.memberId = m.id
    WHERE m.id = :memberId
    """
    )
    fun homeMemberInfo(
        @Param("memberId") memberId: Long
    ): List<HomeMemberInfoDto>

}