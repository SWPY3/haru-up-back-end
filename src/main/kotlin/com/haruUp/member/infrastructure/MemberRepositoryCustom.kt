package com.haruUp.member.infrastructure

import com.haruUp.member.domain.dto.HomeMemberInfoDto
import com.haruUp.member.domain.dto.MemberStatisticsDto

interface MemberRepositoryCustom {
    fun homeMemberInfo(memberId: Long): List<HomeMemberInfoDto>

    fun memberStatisticsList(): List<MemberStatisticsDto>
}
