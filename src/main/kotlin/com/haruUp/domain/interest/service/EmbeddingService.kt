package com.haruUp.domain.interest.service

import com.haruUp.domain.interest.model.InterestNode
import com.haruUp.domain.interest.repository.EmbeddingQueueRepository
import com.haruUp.domain.interest.repository.InterestRepository
import com.haruUp.domain.interest.repository.VectorInterestRepository
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.LocalDateTime

/**
 * 임베딩 설정
 */
object EmbeddingConfig {
    const val MIN_USAGE_COUNT = 3        // 최소 3명이 사용해야 임베딩
    const val EMBEDDING_DELAY_DAYS = 7   // 7일 후 검증
    const val BATCH_SIZE = 100           // 배치 처리 크기
}

/**
 * 임베딩 서비스
 *
 * - Clova Embedding API를 사용하여 텍스트를 벡터로 변환
 * - 검증된 관심사만 점진적으로 임베딩
 * - 배치 처리로 비용 최적화
 */
@Service
class EmbeddingService(
    private val clovaEmbeddingClient: com.haruUp.global.clova.ClovaEmbeddingClient,
    private val vectorRepository: VectorInterestRepository,
    private val interestRepository: InterestRepository,
    private val queueRepository: EmbeddingQueueRepository
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 텍스트를 임베딩 벡터로 변환
     */
    suspend fun embed(text: String): List<Float> {
        return clovaEmbeddingClient.createEmbedding(text)
    }

    /**
     * 여러 텍스트를 한번에 임베딩 (배치 처리)
     */
    suspend fun embedBatch(texts: List<String>): List<List<Float>> {
        return clovaEmbeddingClient.createEmbeddingBatch(texts)
    }

    /**
     * 임베딩 대기 큐에 추가
     */
    fun addToQueue(interest: InterestNode) {
        queueRepository.save(interest)
        logger.info("임베딩 큐에 추가: ${interest.name}")
    }

    /**
     * 배치 임베딩 실행 (매일 새벽 3시)
     */
    @Scheduled(cron = "0 0 3 * * *")
    fun batchEmbedding() = runBlocking {
        logger.info("=== 배치 임베딩 시작 ===")

        // 1. 임베딩 후보 조회
        val candidates = queueRepository.findAll()
            .filter { isEligibleForEmbedding(it) }

        if (candidates.isEmpty()) {
            logger.info("임베딩 대상 없음")
            return@runBlocking
        }

        logger.info("임베딩 대상: ${candidates.size}개")

        // 2. 배치 단위로 처리
        candidates.chunked(EmbeddingConfig.BATCH_SIZE).forEachIndexed { batchIndex, batch ->
            try {
                processBatch(batch, batchIndex + 1)
            } catch (e: Exception) {
                logger.error("배치 ${batchIndex + 1} 처리 실패: ${e.message}", e)
            }
        }

        logger.info("=== 배치 임베딩 완료 ===")
    }

    /**
     * 임베딩 자격 확인
     */
    private fun isEligibleForEmbedding(interest: InterestNode): Boolean {
        return !interest.isEmbedded &&
                interest.usageCount >= EmbeddingConfig.MIN_USAGE_COUNT &&
                interest.createdAt.isBefore(
                    LocalDateTime.now().minusDays(EmbeddingConfig.EMBEDDING_DELAY_DAYS.toLong())
                )
    }

    /**
     * 배치 처리
     */
    private suspend fun processBatch(batch: List<InterestNode>, batchNumber: Int) {
        logger.info("배치 $batchNumber 처리 시작 (${batch.size}개)")

        val texts = batch.map { it.toEmbeddingText() }

        try {
            // 임베딩 생성 (배치)
            val embeddings = embedBatch(texts)

            // Vector DB에 저장
            batch.zip(embeddings).forEach { (interest, embedding) ->
                try {
                    vectorRepository.insert(
                        interestId = interest.id,
                        name = interest.name,
                        level = interest.level,
                        parentName = interest.parentName,
                        fullPath = interest.fullPath,
                        embedding = embedding,
                        metadata = mapOf(
                            "usageCount" to interest.usageCount,
                            "createdAt" to interest.createdAt.toString(),
                            "isUserGenerated" to interest.isUserGenerated
                        )
                    )

                    // 상태 업데이트
                    interest.isEmbedded = true
                    interest.embeddedAt = LocalDateTime.now()
                    interestRepository.save(interest)

                    // 큐에서 제거
                    queueRepository.delete(interest)

                    logger.info("✓ 임베딩 완료: ${interest.name}")

                } catch (e: Exception) {
                    logger.error("✗ 임베딩 저장 실패: ${interest.name} - ${e.message}")
                }
            }

            logger.info("배치 $batchNumber 완료")

        } catch (e: Exception) {
            logger.error("배치 $batchNumber 임베딩 생성 실패: ${e.message}", e)
        }
    }

    /**
     * 초기 데이터 임베딩 (관리자용)
     */
    suspend fun embedInitialData(interests: List<InterestNode>) {
        logger.info("=== 초기 데이터 임베딩 시작 (${interests.size}개) ===")

        interests.chunked(EmbeddingConfig.BATCH_SIZE).forEachIndexed { index, batch ->
            try {
                processBatch(batch, index + 1)
            } catch (e: Exception) {
                logger.error("초기 임베딩 배치 ${index + 1} 실패: ${e.message}", e)
            }
        }

        logger.info("=== 초기 데이터 임베딩 완료 ===")
    }
}

/**
 * InterestNode 확장 함수
 */
private fun InterestNode.toEmbeddingText(): String {
    // 임베딩 텍스트 생성 (컨텍스트 포함)
    return when {
        parentName != null -> "$parentName - $name"
        else -> name
    }
}
