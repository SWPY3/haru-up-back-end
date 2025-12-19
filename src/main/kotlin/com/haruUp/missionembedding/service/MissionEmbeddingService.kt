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
    private val missionEmbeddingRepository: MissionEmbeddingRepository,
    private val clovaEmbeddingClient: ClovaEmbeddingClient
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 미션 내용을 임베딩하여 저장
     *
     * @param directFullPath 전체 경로 배열 (예: ["외국어 공부", "영어", "단어 학습"])
     * @param difficulty 난이도
     * @param missionContent 미션 내용
     * @return 저장된 미션 임베딩 엔티티
     */
    @Transactional
    suspend fun embedAndSaveMission(
        directFullPath: List<String>,
        difficulty: Int?,
        missionContent: String
    ): MissionEmbeddingEntity {
        val directFullPathPostgres = listToPostgresArray(directFullPath)

        // 중복 체크
        val existing = missionEmbeddingRepository.findByMissionContentAndCategory(
            missionContent = missionContent,
            directFullPath = directFullPathPostgres
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
        val interestPath = MissionEmbeddingEntity.categoryPathToString(directFullPath)
        val embeddingText = buildEmbeddingText(interestPath, difficulty, missionContent)
        val embeddingVector = clovaEmbeddingClient.createEmbedding(embeddingText)
        val embeddingString = MissionEmbeddingEntity.vectorToString(embeddingVector)

        // 새로운 미션 임베딩 저장 (custom insert method with type casting)
        missionEmbeddingRepository.insertMissionEmbedding(
            directFullPath = directFullPathPostgres,
            difficulty = difficulty,
            missionContent = missionContent,
            embedding = embeddingString,
            usageCount = 1,
            isActivated = true,
            createdAt = java.time.LocalDateTime.now()
        )

        // 저장된 엔티티 반환 (저장 후 조회)
        return missionEmbeddingRepository.findByMissionContentAndCategory(
            missionContent = missionContent,
            directFullPath = directFullPathPostgres
        ) ?: throw IllegalStateException("미션 임베딩 저장 실패: $missionContent")
    }

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
     * 유사한 미션 검색 (RAG)
     *
     * @param directFullPath 전체 경로 배열 (예: ["외국어 공부", "영어", "단어 학습"])
     * @param difficulty 난이도
     * @param memberProfile 멤버 프로필 정보 (검색 쿼리 생성용)
     * @param limit 반환할 결과 개수
     * @return 유사한 미션 목록
     */
    suspend fun findSimilarMissions(
        directFullPath: List<String>,
        difficulty: Int?,
        memberProfile: String? = null,
        limit: Int = 5
    ): List<MissionEmbeddingEntity> {
        val directFullPathPostgres = listToPostgresArray(directFullPath)

        // 관심사 경로 생성
        val interestPath = MissionEmbeddingEntity.categoryPathToString(directFullPath)

        // 검색 쿼리 생성
        val queryText = buildSearchQuery(interestPath, difficulty, memberProfile)

        // 쿼리 임베딩 생성
        val queryEmbedding = clovaEmbeddingClient.createEmbedding(queryText)
        val embeddingString = MissionEmbeddingEntity.vectorToString(queryEmbedding)

        // 벡터 유사도 검색
        return missionEmbeddingRepository.findByVectorSimilarity(
            embedding = embeddingString,
            directFullPath = directFullPathPostgres,
            difficulty = difficulty,
            limit = limit
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

    /**
     * 인기 미션 조회
     */
    fun findPopularMissions(
        directFullPath: List<String>,
        difficulty: Int?,
        limit: Int = 5
    ): List<MissionEmbeddingEntity> {
        val directFullPathPostgres = listToPostgresArray(directFullPath)
        return missionEmbeddingRepository.findPopularMissions(
            directFullPath = directFullPathPostgres,
            difficulty = difficulty,
            limit = limit
        )
    }

    /**
     * 난이도별 미션 1개씩 조회 (대분류 필터 + 임베딩 유사도 + usage_count 우선)
     *
     * 1. 대분류(directFullPath[0])로 먼저 필터링
     * 2. 관심사 경로로 쿼리 임베딩 생성
     * 3. 유사한 미션 중 usage_count 높은 순으로 난이도별 1개씩 반환
     *
     * @param directFullPath 전체 경로 배열 (예: ["체력관리 및 운동", "헬스", "근력 키우기"])
     * @return 난이도별 미션 목록 (최대 5개)
     */
    suspend fun findOnePerDifficulty(directFullPath: List<String>): List<MissionEmbeddingEntity> {
        if (directFullPath.isEmpty()) {
            logger.warn("RAG 조회 - directFullPath가 비어있음")
            return emptyList()
        }

        val majorCategory = directFullPath[0]  // 대분류
        val interestPath = MissionEmbeddingEntity.categoryPathToString(directFullPath)
        logger.info("RAG 조회 - majorCategory: $majorCategory, interestPath: $interestPath")

        // 쿼리 임베딩 생성
        val queryEmbedding = clovaEmbeddingClient.createEmbedding(interestPath)
        val embeddingString = MissionEmbeddingEntity.vectorToString(queryEmbedding)

        val result = missionEmbeddingRepository.findOnePerDifficulty(embeddingString, majorCategory)
        logger.info("RAG 조회 - 결과: ${result.size}개, IDs: ${result.map { it.id }}")
        return result
    }

    /**
     * 미션 선택 시 임베딩 생성 및 업데이트
     *
     * @param missionId mission_embeddings 테이블의 ID
     * @return 업데이트된 엔티티 (없으면 null)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    suspend fun generateAndUpdateEmbedding(missionId: Long): MissionEmbeddingEntity? {
        // 1. 미션 조회
        val missionEntity = missionEmbeddingRepository.findById(missionId).orElse(null)
            ?: run {
                logger.error("미션을 찾을 수 없음: missionId=$missionId")
                return null
            }

        // 2. 이미 embedding이 있으면 usage_count만 증가
        if (missionEntity.embedding != null) {
            logger.info("이미 embedding 존재, usage_count만 증가: missionId=$missionId")
            missionEmbeddingRepository.incrementUsageCount(
                id = missionId,
                updatedAt = java.time.LocalDateTime.now()
            )
            return missionEmbeddingRepository.findById(missionId).orElse(null)
        }

        // 3. 임베딩 생성
        val interestPath = missionEntity.getInterestPath()
        val embeddingText = buildEmbeddingText(
            interestPath = interestPath,
            difficulty = missionEntity.difficulty,
            missionContent = missionEntity.missionContent
        )
        val embeddingVector = clovaEmbeddingClient.createEmbedding(embeddingText)
        val embeddingString = MissionEmbeddingEntity.vectorToString(embeddingVector)

        // 4. 임베딩 업데이트
        missionEmbeddingRepository.updateEmbedding(
            id = missionId,
            embedding = embeddingString,
            updatedAt = java.time.LocalDateTime.now()
        )

        logger.info("미션 임베딩 생성 및 업데이트 완료: missionId=$missionId")

        return missionEmbeddingRepository.findById(missionId).orElse(null)
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
        memberProfile: String?
    ): String {
        val parts = mutableListOf<String>()
        parts.add(interestPath)

        difficulty?.let {
            parts.add("난이도 $it")
        }

        memberProfile?.let {
            parts.add(it)
        }

        return parts.joinToString(" ")
    }
}
