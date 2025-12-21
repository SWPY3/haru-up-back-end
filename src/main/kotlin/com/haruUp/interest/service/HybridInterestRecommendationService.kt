package com.haruUp.interest.service

import com.haruUp.interest.model.*
import com.haruUp.interest.repository.InterestEmbeddingJpaRepository
import com.haruUp.interest.repository.VectorInterestRepository
import com.haruUp.member.domain.MemberProfile
import com.haruUp.global.util.PostgresArrayUtils.listToPostgresArray
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.*

/**
 * RAG + AI 하이브리드 관심사 추천 서비스
 *
 * 동작 방식:
 * 1. Vector DB에서 임베딩 기반 유사 관심사 검색 (RAG)
 * 2. 결과가 부족하면 AI로 추가 추천
 * 3. 사용자 직접 입력 지원
 * 4. 검증된 관심사만 점진적으로 임베딩에 추가
 */
@Service
class HybridInterestRecommendationService(
    private val vectorRepository: VectorInterestRepository,
    private val embeddingRepository: InterestEmbeddingJpaRepository,
    private val aiRecommender: AIInterestRecommender
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val RAG_RATIO = 0.7  // RAG로 70% 추천 시도
        private const val MIN_SIMILARITY_SCORE = 0.975f  // 최소 유사도 (Clova Embedding은 유사도가 매우 높음, 다른 대분류는 0.97 이하)
    }

    /**
     * 하이브리드 추천
     *
     * @param selectedInterests 사용자가 이미 선택한 관심사들
     * @param currentLevel 추천받을 레벨 (MAIN, MIDDLE, SUB)
     * @param targetCount 추천받을 개수 (기본 10개)
     * @param memberProfile 멤버 프로필
     * @param useHybridScoring true: 유사도 + 인기도 결합 점수 사용, false: 유사도만 사용 (기본값)
     * @return 추천 결과
     */
    suspend fun recommend(
        selectedInterests: List<InterestPath>,
        currentLevel: InterestLevel,
        targetCount: Int = 10,
        memberProfile: MemberProfile,
        useHybridScoring: Boolean = false
    ): RecommendationResult {
        val scoringMode = if (useHybridScoring) "하이브리드(유사도+인기도)" else "유사도만"
        logger.info("추천 요청 - 레벨: $currentLevel, 목표: ${targetCount}개, 스코어링: $scoringMode")

        // Step 1: RAG 기반 추천 (70% 목표)
        val ragTargetCount = (targetCount * RAG_RATIO).toInt()
        val ragResults = searchFromVectorDB(
            selectedInterests = selectedInterests,
            level = currentLevel,
            topK = ragTargetCount,
            useHybridScoring = useHybridScoring
        )

        logger.info("RAG 추천: ${ragResults.size}개 (목표: ${ragTargetCount}개)")

        // Step 2: 부족하면 AI로 보완
        val aiResults = if (ragResults.size < targetCount) {
            val aiTargetCount = targetCount - ragResults.size
            logger.info("AI 추가 추천 필요: ${aiTargetCount}개")

            recommendFromAI(
                selectedInterests = selectedInterests,
                currentLevel = currentLevel,
                excludeNames = ragResults.map { it.name },
                count = aiTargetCount,
                memberProfile = memberProfile
            )
        } else {
            emptyList()
        }

        logger.info("AI 추천: ${aiResults.size}개")

        // Step 3: 결과 통합 및 중복 제거
        val combined = (ragResults + aiResults)
            .distinctBy { it.name.lowercase().trim() }
            .take(targetCount)

        return RecommendationResult(
            interests = combined,
            ragCount = ragResults.size,
            aiCount = aiResults.size,
            totalCount = combined.size,
            usedHybridScoring = useHybridScoring
        )
    }

    /**
     * Vector DB에서 유사 관심사 검색 (RAG)
     */
    private suspend fun searchFromVectorDB(
        selectedInterests: List<InterestPath>,
        level: InterestLevel,
        topK: Int,
        useHybridScoring: Boolean = false
    ): List<InterestNode> {
        return try {
            if (selectedInterests.isEmpty()) {
                // 선택된 게 없으면 인기 있는 항목 반환
                embeddingRepository.findPopularByLevel(level.name, topK)
                    .map { it.toInterestNode() }
            } else {
                // 여러 관심사 기반 검색
                if (useHybridScoring) {
                    // 하이브리드 스코어링: 유사도 + 인기도
                    vectorRepository.searchSimilarMultipleWithHybridScore(
                        queries = selectedInterests.map { it.toPathString() },
                        level = level,
                        topK = topK,
                        minScore = MIN_SIMILARITY_SCORE
                    )
                } else {
                    // 기존 방식: 유사도만
                    vectorRepository.searchSimilarMultiple(
                        queries = selectedInterests.map { it.toPathString() },
                        level = level,
                        topK = topK,
                        minScore = MIN_SIMILARITY_SCORE
                    )
                }
            }
        } catch (e: Exception) {
            logger.error("RAG 검색 실패: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * AI로 추가 추천
     */
    private fun recommendFromAI(
        selectedInterests: List<InterestPath>,
        currentLevel: InterestLevel,
        excludeNames: List<String>,
        count: Int,
        memberProfile: MemberProfile
    ): List<InterestNode> {
        return try {
            aiRecommender.recommend(
                selectedInterests = selectedInterests,
                currentLevel = currentLevel,
                excludeNames = excludeNames,
                count = count,
                memberProfile = memberProfile
            )
        } catch (e: Exception) {
            logger.error("AI 추천 실패: ${e.message}", e)
            emptyList()
        }
    }
}

/**
 * 추천 결과
 */
data class RecommendationResult(
    val interests: List<InterestNode>,
    val ragCount: Int,
    val aiCount: Int,
    val totalCount: Int,
    val usedHybridScoring: Boolean = false
) {
    val ragRatio: Double = if (totalCount > 0) ragCount.toDouble() / totalCount else 0.0
    val aiRatio: Double = if (totalCount > 0) aiCount.toDouble() / totalCount else 0.0

    fun summary(): String {
        val scoringMode = if (usedHybridScoring) "하이브리드 스코어링" else "유사도 스코어링"
        return "총 ${totalCount}개 (RAG: ${ragCount}개 ${(ragRatio * 100).toInt()}%, AI: ${aiCount}개 ${(aiRatio * 100).toInt()}%) [$scoringMode]"
    }
}

/**
 * 직접 입력 결과
 */
data class DirectInputResult(
    val interest: InterestNode,
    val isNew: Boolean,
    val similarInterests: List<InterestNode>
)
