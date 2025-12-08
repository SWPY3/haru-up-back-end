package com.haruUp.character.infrastructure

import com.haruUp.character.domain.Level
import org.springframework.data.jpa.repository.JpaRepository

interface LevelRepository : JpaRepository<Level, Long> {

    fun findByLevelNumber(levelNumber : Int) : Level?

}