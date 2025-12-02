package com.haruUp.interest.application

import com.haruUp.interest.domain.InterestDto
import com.haruUp.interest.infrastructure.InterestRepository
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class InterestService(
    private val interestRepository: InterestRepository,
    private val clovaApiService: ClovaApiService
) {

    companion object {
        private val logger = LoggerFactory.getLogger(InterestService::class.java)
    }

    /**
     * 관심사 트리 조회
     * @param parentId 부모 ID (null이면 최상위 조회)
     * @param depth 깊이 (1:대분류, 2:중분류, 3:소분류)
     * @return 관심사 목록
     */
    @Transactional
    fun getInterests(parentId: Long?, depth: Int?): List<InterestDto> {
        logger.info("api 요청: parentId={}, depth={}", parentId, depth)

        val entities = when {
            // parentId와 depth가 모두 주어진 경우
            parentId != null && depth != null -> {
                interestRepository.findByDepthAndParentIdAndDeletedFalse(depth, parentId)
                    .map { it.toDto() }
            }
            // parentId만 주어진 경우 - 해당 부모의 자식 조회
            parentId != null -> {
                interestRepository.findByParentIdAndDeletedFalse(parentId)
                    .map { it.toDto() }
            }
            // depth만 주어진 경우 - 해당 depth의 모든 항목 조회
            depth != null -> {
                interestRepository.findByDepthAndDeletedFalse(depth)
                    .map { it.toDto() }
            }
            // 둘 다 없으면 기본적으로 대분류(depth=1) 조회
            else -> {
                interestRepository.findByDepthAndDeletedFalse(1)
                    .map { it.toDto() }
            }
        }

        logger.info("Repository 조회 완료: 조회된 관심사 개수={}", entities)

        return entities
    }

    /**
     * AI 추천 관심사 목록 조회
     * Clova API를 호출하여 추천받은 관심사를 반환
     * @param parentId 부모 관심사 ID (중/소분류 선택 시)
     * @param message 사용자가 입력한 관심사 내용
     */
    @Transactional
    fun getAiRecommendInterests(parentId: Long?, message: String?): List<InterestDto> {
        // parentId로 최상위까지 경로 조회 및 조합
        val interestPath = if (parentId != null) {
            buildInterestPath(parentId)
        } else {
            ""
        }

        // message 추가
        val fullPath = if (message.isNullOrBlank()) {
            interestPath
        } else {
            if (interestPath.isBlank()) message else "$interestPath > $message"
        }

        logger.info("관심사 경로: {}", fullPath)

        // 동적 프롬프트 생성
        val prompt = if (fullPath.isNotBlank()) {
            "사용자가 '$fullPath' 관심사에 관심이 있습니다. 이와 관련하여 사용자에게 추천할 만한 세부 관심사 카테고리를 정확히 5개 추천해주세요. 각 관심사는 쉼표로 구분하고, 간단한 이름만 나열해주세요."
        } else {
            "사용자에게 추천할 만한 인기 관심사 카테고리를 정확히 5개 추천해주세요. 각 관심사는 쉼표로 구분하고, 간단한 이름만 나열해주세요."
        }

        // Clova API 호출
        val recommendedKeywords = clovaApiService.getRecommendedInterests(prompt)

        logger.info("AI 추천 관심사: {}", recommendedKeywords)

        // 추천받은 키워드가 있으면 그대로 반환 (InterestDto 형태로)
        // parentId가 있으면 해당 depth+1의 관심사들을 조회해서 매칭
        return if (recommendedKeywords.isEmpty()) {
            logger.warn("AI 추천 실패, 빈 목록 반환")
            emptyList()
        } else {
            // AI가 추천한 키워드를 그대로 InterestDto로 변환하여 반환
            recommendedKeywords.mapIndexed { index, keyword ->
                InterestDto(
                    id = null,
                    parentId = parentId,
                    depth = if (parentId != null) {
                        interestRepository.findById(parentId).map { it.depth + 1 }.orElse(1)
                    } else {
                        1
                    },
                    interestName = keyword,
                    normalizedKey = null,
                    createdSource = com.haruUp.interest.domain.CreatedSourceType.AI
                )
            }.take(5)
        }
    }

    /**
     * parentId로부터 최상위 관심사까지 경로를 조회하여 "대분류 > 중분류" 형태로 반환
     */
    private fun buildInterestPath(parentId: Long): String {
        val path = mutableListOf<String>()
        var currentId: Long? = parentId

        while (currentId != null) {
            val interest = interestRepository.findById(currentId).orElse(null)
            if (interest != null) {
                path.add(0, interest.interestName) // 앞에 추가 (역순)
                currentId = interest.parentId
            } else {
                break
            }
        }

        return path.joinToString(" > ")
    }
}
