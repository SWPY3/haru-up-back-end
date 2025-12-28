package com.haruUp.missionembedding.service

import com.haruUp.missionembedding.entity.MissionEmbeddingEntity
import com.haruUp.missionembedding.repository.MissionEmbeddingRepository
import com.haruUp.global.clova.ClovaEmbeddingClient
import com.haruUp.global.util.PostgresArrayUtils.listToPostgresArray
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * 미션 임베딩 서비스
 *
 * 미션을 임베딩하고, 유사한 미션을 검색하는 기능 제공
 */
@Service
class MissionEmbeddingService(
    private val missionEmbeddingRepository: MissionEmbeddingRepository
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 미션 내용을 embedding 없이 저장 (추천 시점에 즉시 저장용)
     *
     * @param directFullPath 전체 경로 배열 (예: ["외국어 공부", "영어", "단어 학습"])
     * @param difficulty 난이도
     * @param missionContent 미션 내용
     * @return 저장된 미션 임베딩 엔티티
     */
    @Transactional
    fun saveMissionWithoutEmbedding(
        directFullPath: List<String>,
        difficulty: Int?,
        missionContent: String
    ): MissionEmbeddingEntity? {
        val directFullPathPostgres = listToPostgresArray(directFullPath)

        // 중복 체크
        val existing = missionEmbeddingRepository.findByMissionContentAndCategory(
            missionContent = missionContent,
            directFullPath = directFullPathPostgres
        )

        if (existing != null) {
            // 이미 존재하면 사용 횟수만 증가
            missionEmbeddingRepository.incrementUsageCount(
                id = existing.id!!,
                updatedAt = java.time.LocalDateTime.now()
            )
            logger.info("기존 미션 발견, usage_count 증가: $missionContent")
            return missionEmbeddingRepository.findById(existing.id!!)
                .orElse(null)
        }

        // 새로운 미션 저장 (embedding = null)
        missionEmbeddingRepository.insertMissionEmbedding(
            directFullPath = directFullPathPostgres,
            difficulty = difficulty,
            missionContent = missionContent,
            embedding = null,
            usageCount = 0,
            isActivated = true,
            createdAt = java.time.LocalDateTime.now()
        )

        logger.info("미션 저장 완료 (embedding 없음): $missionContent")

        // 저장된 엔티티 반환
        return missionEmbeddingRepository.findByMissionContentAndCategory(
            missionContent = missionContent,
            directFullPath = directFullPathPostgres
        )
    }

    /**
     * ID 목록으로 미션 조회
     *
     * @param ids 미션 ID 목록
     * @return 미션 임베딩 엔티티 목록
     */
    fun findByIds(ids: List<Long>): List<MissionEmbeddingEntity> {
        return missionEmbeddingRepository.findAllById(ids)
    }
}
