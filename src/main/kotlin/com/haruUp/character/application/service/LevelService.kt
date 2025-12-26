package com.haruUp.character.application.service

import com.haruUp.character.domain.Level
import com.haruUp.character.infrastructure.LevelRepository
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service

@Service
class LevelService(
    private val levelRepository: LevelRepository
) {

    /**
     * levelNumber가 존재하지 않으면 규칙에 따라 생성해서 저장하고 반환.
     */
    @Transactional
    fun getOrCreateLevel(levelNumber: Int): Level {

        // 이미 존재하면 반환
        levelRepository.findByLevelNumber(levelNumber)?.let { return it }

        // 규칙 기반 자동 생성
        val requiredExp = calculateRequiredExp(levelNumber)
        val maxExp = calculateMaxExp(levelNumber)

        val newLevel = Level(
            levelNumber = levelNumber,
            requiredExp = requiredExp,
            maxExp = maxExp
        )

        return levelRepository.save(newLevel)
    }


    @Transactional
    fun getInitialLevelId(): Long =
        getOrCreateLevel(1).id
            ?: throw IllegalStateException("Level 1 could not be created")

    @Transactional
    fun getById(levelId: Long): Level =
        levelRepository.findById(levelId).orElseThrow()



    /**
     * 다음 레벨 조회 (없으면 생성)
     */
    @Transactional
    fun getNextLevel(levelNumber: Int): Level? {
        val nextLevelNumber = levelNumber + 1

        // 원하는 정책 선택 가능: 없으면 null vs 자동 생성
        // 지금은 "없으면 생성" 방식으로 할게.
        return getOrCreateLevel(nextLevelNumber)
    }



    /**
     * 🔥 캐릭터 레벨업 기준 (구간 기준)
     */
    fun calculateMaxExp(levelNumber: Int): Int {
        return 1000
    }

    /**
     * 📊 다음 레벨까지 필요 경험치 (UI/밸런스용)
     */
    fun calculateRequiredExp(levelNumber: Int): Int {
        return 1000
    }
}