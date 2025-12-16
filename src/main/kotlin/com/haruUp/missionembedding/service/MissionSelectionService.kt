package com.haruUp.missionembedding.service

import com.haruUp.interest.entity.InterestEmbeddingEntity
import com.haruUp.interest.entity.MemberInterestEntity
import com.haruUp.interest.model.InterestLevel
import com.haruUp.interest.repository.InterestEmbeddingJpaRepository
import com.haruUp.missionembedding.dto.MissionSelectionRequest
import com.haruUp.missionembedding.dto.MissionSelectionResponse
import com.haruUp.global.util.PostgresArrayUtils.listToPostgresArray
import com.haruUp.mission.domain.MemberMission
import com.haruUp.mission.infrastructure.MemberMissionRepository
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
 * member_interest 테이블에 사용자-관심사 연결 저장 (directFullPath 포함)
 */
@Service
class MissionSelectionService(
    private val missionEmbeddingService: MissionEmbeddingService,
    private val memberMissionRepository: MemberMissionRepository,
    private val interestEmbeddingJpaRepository: InterestEmbeddingJpaRepository,
    private val memberInterestRepository: com.haruUp.interest.repository.MemberInterestJpaRepository
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 사용자가 선택한 미션들의 임베딩을 생성하고 사용자와 연결
     *
     * @param memberId 사용자 ID
     * @param request 미션 선택 요청
     * @return 저장된 미션 정보
     */
    fun saveMissions(memberId: Long, request: MissionSelectionRequest): MissionSelectionResponse {
        logger.info("미션 선택 요청 - 사용자: $memberId, 미션 개수: ${request.missions.size}")

        val savedMemberMissionIds = mutableListOf<Long>()

        request.missions.forEach { dto ->
            try {
                if (dto.directFullPath.isEmpty()) {
                    logger.error("directFullPath가 비어있습니다: ${dto.directFullPath}")
                    return@forEach
                }

                // 0. parentId가 소분류(SUB)인지 validation
                val parentInterest = interestEmbeddingJpaRepository.findById(dto.parentId).orElse(null)
                if (parentInterest == null) {
                    logger.error("parentId에 해당하는 관심사를 찾을 수 없습니다: parentId=${dto.parentId}")
                    throw IllegalArgumentException("parentId에 해당하는 관심사를 찾을 수 없습니다: parentId=${dto.parentId}")
                }
                if (parentInterest.level != InterestLevel.SUB) {
                    logger.error("parentId는 소분류(SUB)만 사용 가능합니다: parentId=${dto.parentId}, level=${parentInterest.level}")
                    throw IllegalArgumentException("parentId는 소분류(SUB)만 사용 가능합니다. 현재 level: ${parentInterest.level}")
                }

                // 1. 관심사 저장/업데이트 (계층 구조 전체 처리)
                saveOrUpdateInterestHierarchy(
                    parentId = dto.parentId,
                    directFullPath = dto.directFullPath
                )

                // 2. 미션 임베딩 생성 및 업데이트 (Clova API로 벡터 계산)
                runBlocking {
                    missionEmbeddingService.generateAndUpdateEmbedding(dto.missionId)
                }

                // 3. 사용자-미션 연결 저장 (중복 체크 없이 항상 새로운 row 생성)
                val memberMission = MemberMission(
                    memberId = memberId,
                    missionId = dto.missionId,
                    expEarned = 0
                )
                val saved = memberMissionRepository.saveAndFlush(memberMission)
                saved.id?.let { savedMemberMissionIds.add(it) }
                logger.info("사용자-미션 연결 저장 완료: 사용자=$memberId, missionId=${dto.missionId}")

                // 4. member_interest 테이블에 사용자-관심사 연결 저장 (directFullPath 포함)
                saveMemberInterest(
                    userId = memberId,
                    parentId = dto.parentId,
                    directFullPath = dto.directFullPath
                )
            } catch (e: Exception) {
                logger.error("미션 선택 실패: missionId=${dto.missionId}, 에러: ${e.message}", e)
            }
        }

        logger.info("미션 선택 완료 - 저장된 개수: ${savedMemberMissionIds.size}")

        return MissionSelectionResponse(
            savedCount = savedMemberMissionIds.size,
            missionIds = savedMemberMissionIds
        )
    }

    /**
     * 관심사 계층 구조 저장/업데이트
     *
     * directFullPath를 기반으로 대분류, 중분류, 소분류를 순차적으로 처리
     * - 기존에 있으면 usage_count만 증가
     * - 없으면 새로 생성 (embedding NULL, is_activated false, created_source = USER)
     */
    private fun saveOrUpdateInterestHierarchy(
        parentId: Long,
        directFullPath: List<String>
    ) {
        val now = LocalDateTime.now()

        // directFullPath 길이에 따라 처리
        val mainCategory = directFullPath.getOrNull(0) ?: return
        val middleCategory = directFullPath.getOrNull(1)
        val subCategory = directFullPath.getOrNull(2)

        // 1. 대분류 처리
        val mainFullPathList = listOf(mainCategory)
        val mainInterest = saveOrUpdateInterest(
            fullPathList = mainFullPathList,
            name = mainCategory,
            level = InterestLevel.MAIN,
            parentId = null,
            createdSource = "USER",  // 직접입력은 USER로 처리
            now = now
        )

        // 2. 중분류 처리 (있는 경우)
        val middleInterest = middleCategory?.let { middle ->
            val middleFullPathList = listOf(mainCategory, middle)
            saveOrUpdateInterest(
                fullPathList = middleFullPathList,
                name = middle,
                level = InterestLevel.MIDDLE,
                parentId = mainInterest?.id?.toString(),
                createdSource = "USER",
                now = now
            )
        }

        // 3. 소분류 처리 (있는 경우)
        if (middleCategory != null && subCategory != null && middleInterest != null) {
            val subFullPathList = listOf(mainCategory, middleCategory, subCategory)
            saveOrUpdateInterest(
                fullPathList = subFullPathList,
                name = subCategory,
                level = InterestLevel.SUB,
                parentId = middleInterest.id?.toString(),
                createdSource = "USER",
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
        fullPathList: List<String>,
        name: String,
        level: InterestLevel,
        parentId: String?,
        createdSource: String,
        now: LocalDateTime
    ): InterestEmbeddingEntity? {
        val fullPathPostgresArray = listToPostgresArray(fullPathList)

        // 기존 관심사 조회 (PostgreSQL 배열 형식으로 조회)
        val existingId = interestEmbeddingJpaRepository.findIdByFullPath(fullPathPostgresArray)

        return if (existingId != null) {
            // 기존에 있으면 usage_count만 증가
            interestEmbeddingJpaRepository.incrementUsageCountByFullPath(
                fullPath = fullPathPostgresArray,
                updatedAt = now
            )
            logger.info("관심사 usage_count 증가: fullPath=$fullPathList")
            interestEmbeddingJpaRepository.findById(existingId).orElse(null)
        } else {
            // 없으면 새로 생성 (embedding NULL, is_activated false)
            // Native Query를 사용하여 vector 타입 캐스팅
            interestEmbeddingJpaRepository.insertEmbedding(
                name = name,
                level = level.name,
                parentId = parentId,
                fullPath = fullPathPostgresArray,
                embedding = null,  // 임베딩 NULL (Native Query에서 CAST 처리)
                usageCount = 1,
                createdSource = createdSource,  // 입력받은 created_source 사용
                isActivated = false,  // 비활성 상태
                createdAt = now
            )
            logger.info("새 관심사 생성: fullPath=$fullPathList, createdSource=$createdSource, isActivated=false")

            // 생성된 엔티티 조회하여 반환
            val newId = interestEmbeddingJpaRepository.findIdByFullPath(fullPathPostgresArray)
            newId?.let { interestEmbeddingJpaRepository.findById(it).orElse(null) }
        }
    }

    /**
     * 사용자-관심사 연결 저장
     *
     * parentId를 사용하여 member_interest에 저장
     * directFullPath도 함께 저장
     */
    private fun saveMemberInterest(
        userId: Long,
        parentId: Long,
        directFullPath: List<String>
    ) {
        logger.info("사용자-관심사 저장 시작: userId=$userId, parentId=$parentId, directFullPath=$directFullPath")

        // 새로운 연결 저장 (중복 체크 없이 항상 새로운 row 생성)
        // directFullPath도 함께 저장
        val memberInterest = MemberInterestEntity(
            memberId = userId,
            interestId = parentId,
            directFullPath = directFullPath
        )
        memberInterestRepository.save(memberInterest)
        logger.info("사용자-관심사 연결 저장 완료: userId=$userId, interestId=$parentId, directFullPath=$directFullPath")
    }
}
