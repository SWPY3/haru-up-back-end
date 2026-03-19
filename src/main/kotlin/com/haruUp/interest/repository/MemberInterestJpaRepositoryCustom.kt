package com.haruUp.interest.repository

interface MemberInterestJpaRepositoryCustom {
    fun softDeleteAllByMemberId(memberId: Long): Int

    fun softDeleteByIdAndMemberId(id: Long, memberId: Long): Int
}
