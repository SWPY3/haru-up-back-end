package com.haruUp.domain.mission.service

import com.haruUp.domain.mission.entity.MissionEmbeddingEntity
import com.haruUp.domain.mission.repository.MissionEmbeddingRepository
import com.haruUp.global.clova.ClovaEmbeddingClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * 미션 임베딩 서비스
 *
 * 미션을 임베딩하고, 유사한 미션을 검색하는 기능 제공
 */
@Service
class MissionEmbeddingService(
    private val missionEmbeddingRepository: MissionEmbeddingRepository,
    private val clovaEmbeddingClient: ClovaEmbeddingClient
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 미션 내용을 임베딩하여 저장
     *
     * @param mainCategory 대분류
     * @param middleCategory 중분류
     * @param subCategory 소분류
     * @param difficulty 난이도
     * @param missionContent 미션 내용
     * @return 저장된 미션 임베딩 엔티티
     */
    suspend fun embedAndSaveMission(
        mainCategory: String,
        middleCategory: String?,
        subCategory: String?,
        difficulty: Int?,
        missionContent: String
    ): MissionEmbeddingEntity {
        // 중복 체크
        val existing = missionEmbeddingRepository.findByMissionContentAndCategory(
            missionContent = missionContent,
            mainCategory = mainCategory,
            middleCategory = middleCategory,
            subCategory = subCategory
        )

        if (existing != null) {
            // 이미 존재하면 사용 횟수만 증가 (custom UPDATE query 사용)
            missionEmbeddingRepository.incrementUsageCount(
                id = existing.id!!,
                updatedAt = java.time.LocalDateTime.now()
            )
            // 업데이트된 엔티티 반환
            return missionEmbeddingRepository.findById(existing.id!!)
                .orElseThrow { IllegalStateException("미션 임베딩 조회 실패: ID ${existing.id}") }
        }

        // 임베딩 생성
        val interestPath = MissionEmbeddingEntity.getCategoryPath(mainCategory, middleCategory, subCategory)
        val embeddingText = buildEmbeddingText(interestPath, difficulty, missionContent)
        val embeddingVector = clovaEmbeddingClient.createEmbedding(embeddingText)
        val embeddingString = MissionEmbeddingEntity.vectorToString(embeddingVector)

        // 새로운 미션 임베딩 저장 (custom insert method with type casting)
        val savedId = missionEmbeddingRepository.insertMissionEmbedding(
            mainCategory = mainCategory,
            middleCategory = middleCategory,
            subCategory = subCategory,
            difficulty = difficulty,
            missionContent = missionContent,
            embedding = embeddingString,
            usageCount = 1,
            isActivated = true,
            createdAt = java.time.LocalDateTime.now()
        )

        // 저장된 엔티티 반환 (ID로 조회)
        return missionEmbeddingRepository.findById(savedId)
            .orElseThrow { IllegalStateException("미션 임베딩 저장 실패: ID $savedId") }
    }

    /**
     * 유사한 미션 검색 (RAG)
     *
     * @param mainCategory 대분류
     * @param middleCategory 중분류
     * @param subCategory 소분류
     * @param difficulty 난이도
     * @param userProfile 사용자 프로필 정보 (검색 쿼리 생성용)
     * @param limit 반환할 결과 개수
     * @return 유사한 미션 목록
     */
    suspend fun findSimilarMissions(
        mainCategory: String,
        middleCategory: String?,
        subCategory: String?,
        difficulty: Int?,
        userProfile: String? = null,
        limit: Int = 5
    ): List<MissionEmbeddingEntity> {
        // 관심사 경로 생성
        val interestPath = MissionEmbeddingEntity.getCategoryPath(mainCategory, middleCategory, subCategory)

        // 검색 쿼리 생성
        val queryText = buildSearchQuery(interestPath, difficulty, userProfile)

        // 쿼리 임베딩 생성
        val queryEmbedding = clovaEmbeddingClient.createEmbedding(queryText)
        val embeddingString = MissionEmbeddingEntity.vectorToString(queryEmbedding)

        // 벡터 유사도 검색
        return missionEmbeddingRepository.findByVectorSimilarity(
            embedding = embeddingString,
            mainCategory = mainCategory,
            middleCategory = middleCategory,
            subCategory = subCategory,
            difficulty = difficulty,
            limit = limit
        )
    }

    /**
     * 인기 미션 조회
     */
    fun findPopularMissions(
        mainCategory: String,
        middleCategory: String?,
        subCategory: String?,
        difficulty: Int?,
        limit: Int = 5
    ): List<MissionEmbeddingEntity> {
        return missionEmbeddingRepository.findPopularMissions(
            mainCategory = mainCategory,
            middleCategory = middleCategory,
            subCategory = subCategory,
            difficulty = difficulty,
            limit = limit
        )
    }

    /**
     * 임베딩용 텍스트 생성
     */
    private fun buildEmbeddingText(
        interestPath: String,
        difficulty: Int?,
        missionContent: String
    ): String {
        val difficultyText = difficulty?.let { "난이도 $it" } ?: "난이도 지정 없음"
        return "$interestPath - $difficultyText: $missionContent"
    }

    /**
     * 검색 쿼리 생성
     */
    private fun buildSearchQuery(
        interestPath: String,
        difficulty: Int?,
        userProfile: String?
    ): String {
        val parts = mutableListOf<String>()
        parts.add(interestPath)

        difficulty?.let {
            parts.add("난이도 $it")
        }

        userProfile?.let {
            parts.add(it)
        }

        return parts.joinToString(" ")
    }
}
