package com.haruUp.character.infrastructure

import com.haruUp.character.domain.MemberCharacter
import org.springframework.data.jpa.repository.JpaRepository

interface MemberCharacterRepository  : JpaRepository<MemberCharacter , Long>{
    fun findByMemberId(memberId: Long): MemberCharacter?
}