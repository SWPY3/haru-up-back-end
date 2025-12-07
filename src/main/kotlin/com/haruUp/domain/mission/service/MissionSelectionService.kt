package com.haruUp.domain.mission.service

import com.haruUp.domain.interest.entity.InterestEmbeddingEntity
import com.haruUp.domain.interest.model.InterestLevel
import com.haruUp.domain.interest.repository.InterestEmbeddingJpaRepository
import com.haruUp.domain.mission.dto.CategoryInfo
import com.haruUp.domain.mission.dto.MissionSelectionRequest
import com.haruUp.domain.mission.dto.MissionSelectionResponse
import com.haruUp.domain.mission.entity.MemberMissionEntity
import com.haruUp.domain.mission.repository.MemberMissionRepository
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * 미션 선택 서비스
 *
 * 사용자가 선택한 미션을 mission_embeddings 테이블에 임베딩하여 저장하고,
 * member_mission 테이블에 사용자-미션 연결 저장
 * interest_embeddings 테이블의 usage_count 증가
 * member_interest 테이블에 사용자-관심사 연결 저장
 */
@Service
class MissionSelectionService(
    private val missionEmbeddingService: MissionEmbeddingService,
    private val memberMissionRepository: MemberMissionRepository,
    private val interestEmbeddingJpaRepository: InterestEmbeddingJpaRepository,
    private val memberInterestRepository: com.haruUp.domain.interest.repository.MemberInterestJpaRepository
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 사용자가 선택한 미션들을 임베딩하여 저장하고 사용자와 연결
     *
     * @param request 미션 선택 요청
     * @return 저장된 미션 정보
     */
    @Transactional
    fun saveMissions(request: MissionSelectionRequest): MissionSelectionResponse {
        logger.info("미션 저장 요청 - 사용자: ${request.userId}, 미션 개수: ${request.missions.size}")

        val savedMemberMissionIds = mutableListOf<Long>()

        request.missions.forEach { dto ->
            try {
                // 1. 관심사 저장/업데이트 (계층 구조 전체 처리)
                saveOrUpdateInterestHierarchy(
                    mainCategory = dto.mainCategory,
                    middleCategory = dto.middleCategory,
                    subCategory = dto.subCategory
                )

                // 2. 미션 임베딩 저장 (중복이면 기존 미션 반환)
                val missionEmbedding = runBlocking {
                    missionEmbeddingService.embedAndSaveMission(
                        mainCategory = dto.mainCategory.text,
                        middleCategory = dto.middleCategory?.text,
                        subCategory = dto.subCategory?.text,
                        difficulty = dto.difficulty,
                        missionContent = dto.mission
                    )
                }

                val missionId = missionEmbedding.id
                    ?: throw IllegalStateException("미션 임베딩 ID가 null입니다")

                // 3. 사용자-미션 연결 저장 (중복 체크 없이 항상 새로운 row 생성)
                val memberMission = MemberMissionEntity(
                    memberId = request.userId,
                    missionId = missionId
                )
                val saved = memberMissionRepository.save(memberMission)
                saved.id?.let { savedMemberMissionIds.add(it) }
                logger.info("사용자-미션 연결 저장 완료: 사용자=${request.userId}, 미션=${dto.mission}")

                // 4. member_interest 테이블에 사용자-관심사 연결 저장
                saveMemberInterest(
                    userId = request.userId,
                    mainCategory = dto.mainCategory.text,
                    middleCategory = dto.middleCategory?.text,
                    subCategory = dto.subCategory?.text
                )
            } catch (e: Exception) {
                logger.error("미션 저장 실패: ${dto.mission}, 에러: ${e.message}", e)
            }
        }

        logger.info("미션 저장 완료 - 저장된 개수: ${savedMemberMissionIds.size}")

        return MissionSelectionResponse(
            savedCount = savedMemberMissionIds.size,
            missionIds = savedMemberMissionIds
        )
    }

    /**
     * 대분류, 중분류, 소분류로 fullPath 구성
     *
     * 예시:
     * - 대분류만: "운동"
     * - 대분류 + 중분류: "운동 > 헬스"
     * - 대분류 + 중분류 + 소분류: "운동 > 헬스 > 근력 키우기"
     */
    private fun buildFullPath(
        mainCategory: String,
        middleCategory: String?,
        subCategory: String?
    ): String {
        val parts = mutableListOf(mainCategory)
        middleCategory?.let { parts.add(it) }
        subCategory?.let { parts.add(it) }
        return parts.joinToString(" > ")
    }

    /**
     * 관심사 계층 구조 저장/업데이트
     *
     * 대분류, 중분류, 소분류를 순차적으로 처리
     * - 기존에 있으면 usage_count만 증가
     * - 없으면 새로 생성 (embedding NULL, is_activated false, created_source 사용)
     */
    private fun saveOrUpdateInterestHierarchy(
        mainCategory: CategoryInfo,
        middleCategory: CategoryInfo?,
        subCategory: CategoryInfo?
    ) {
        val now = LocalDateTime.now()

        // 1. 대분류 처리
        val mainFullPath = mainCategory.text
        val mainInterest = saveOrUpdateInterest(
            fullPath = mainFullPath,
            name = mainCategory.text,
            level = InterestLevel.MAIN,
            parentId = null,
            parentName = null,
            createdSource = mainCategory.createdSource,
            now = now
        )

        // 2. 중분류 처리 (있는 경우)
        val middleInterest = middleCategory?.let { middle ->
            val middleFullPath = "${mainCategory.text} > ${middle.text}"
            saveOrUpdateInterest(
                fullPath = middleFullPath,
                name = middle.text,
                level = InterestLevel.MIDDLE,
                parentId = mainInterest?.id?.toString(),
                parentName = mainCategory.text,
                createdSource = middle.createdSource,
                now = now
            )
        }

        // 3. 소분류 처리 (있는 경우)
        if (middleCategory != null && subCategory != null && middleInterest != null) {
            val subFullPath = "${mainCategory.text} > ${middleCategory.text} > ${subCategory.text}"
            saveOrUpdateInterest(
                fullPath = subFullPath,
                name = subCategory.text,
                level = InterestLevel.SUB,
                parentId = middleInterest.id?.toString(),
                parentName = "${mainCategory.text} > ${middleCategory.text}",
                createdSource = subCategory.createdSource,
                now = now
            )
        }
    }

    /**
     * 관심사 저장 또는 업데이트
     *
     * @return 저장/업데이트된 InterestEmbeddingEntity (없으면 null)
     */
    private fun saveOrUpdateInterest(
        fullPath: String,
        name: String,
        level: InterestLevel,
        parentId: String?,
        parentName: String?,
        createdSource: String,
        now: LocalDateTime
    ): InterestEmbeddingEntity? {
        // 기존 관심사 조회
        val existing = interestEmbeddingJpaRepository.findByFullPath(fullPath)

        return if (existing != null) {
            // 기존에 있으면 usage_count만 증가
            interestEmbeddingJpaRepository.incrementUsageCountByFullPath(
                fullPath = fullPath,
                updatedAt = now
            )
            logger.info("관심사 usage_count 증가: fullPath=$fullPath")
            existing
        } else {
            // 없으면 새로 생성 (embedding NULL, is_activated false)
            // Native Query를 사용하여 vector 타입 캐스팅
            interestEmbeddingJpaRepository.insertEmbedding(
                name = name,
                level = level.name,
                parentId = parentId,
                parentName = parentName,
                fullPath = fullPath,
                embedding = null,  // 임베딩 NULL (Native Query에서 CAST 처리)
                usageCount = 1,
                createdSource = createdSource,  // 입력받은 created_source 사용
                isActivated = false,  // 비활성 상태
                createdAt = now
            )
            logger.info("새 관심사 생성: fullPath=$fullPath, createdSource=$createdSource, isActivated=false")

            // 생성된 엔티티 조회하여 반환
            interestEmbeddingJpaRepository.findByFullPath(fullPath)
        }
    }

    /**
     * 사용자-관심사 연결 저장
     *
     * interest_embeddings 테이블에서 관심사를 조회만 하고,
     * 존재하는 관심사만 member_interest 테이블에 저장
     */
    private fun saveMemberInterest(
        userId: Long,
        mainCategory: String,
        middleCategory: String?,
        subCategory: String?
    ) {
        logger.info("사용자-관심사 저장 시작: userId=$userId, main=$mainCategory, middle=$middleCategory, sub=$subCategory")

        // 가장 하위 레벨의 fullPath 구성
        val fullPath = buildFullPath(mainCategory, middleCategory, subCategory)

        // interest_embeddings에서 조회 (없으면 생성하지 않음)
        val interest = interestEmbeddingJpaRepository.findByFullPath(fullPath)

        if (interest == null) {
            logger.warn("관심사를 찾을 수 없어 member_interest 저장 건너뜀: fullPath=$fullPath")
            return
        }

        val interestId = interest.id ?: run {
            logger.error("관심사 ID가 null: fullPath=$fullPath")
            return
        }

        // 새로운 연결 저장 (중복 체크 없이 항상 새로운 row 생성)
        val memberInterest = com.haruUp.domain.interest.entity.MemberInterestEntity(
            memberId = userId,
            interestId = interestId
        )
        memberInterestRepository.save(memberInterest)
        logger.info("사용자-관심사 연결 저장 완료: userId=$userId, interestId=$interestId, fullPath=$fullPath")
    }
}
