package com.haruUp.character.infrastructure

import com.haruUp.character.domain.Character
import org.springframework.data.jpa.repository.JpaRepository

interface CharacterRepository : JpaRepository<Character , Long> {



}