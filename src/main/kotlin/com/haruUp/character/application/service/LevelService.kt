package com.haruUp.character.application.service

import com.haruUp.character.domain.Level
import com.haruUp.character.infrastructure.LevelRepository
import org.springframework.stereotype.Service

@Service
class LevelService(
    private val levelRepository: LevelRepository
) {

    fun getInitialLevelId(): Long {
        return levelRepository.findByLevelNumber(1).id
            ?: throw IllegalStateException("Level 1 not found")
    }

    fun getById(levelId: Long): Level =
        levelRepository.findById(levelId).orElseThrow()

    fun getNextLevel(levelNumber: Int): Level? =
        levelRepository.findByLevelNumber(levelNumber + 1 as Long)
}