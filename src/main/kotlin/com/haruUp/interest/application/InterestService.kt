package com.haruUp.interest.application

import com.haruUp.global.security.SecurityUtils
import com.haruUp.interest.domain.InterestDto
import com.haruUp.interest.infrastructure.InterestRepository
import com.haruUp.member.domain.type.MemberGender
import com.haruUp.member.infrastructure.MemberProfileRepository
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.Period

@Service
class InterestService(
    private val interestRepository: InterestRepository,
    private val clovaApiService: ClovaApiService,
    private val memberProfileRepository: MemberProfileRepository,
    private val securityUtils: SecurityUtils
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
     * @param interests 관심사 경로 배열 (예: ["프로그래밍", "웹개발"])
     */
    @Transactional
    fun getAiRecommendInterests(interests: List<String>?): List<InterestDto> {
        // 배열을 " > "로 조합하여 경로 생성
        val interestPath = interests?.filter { it.isNotBlank() }?.joinToString(" > ") ?: ""

        // 사용자 프로필 정보 가져오기
        val userInfo = buildUserInfo()

        // Clova API 호출 (관심사 경로가 비어있으면 일반 추천)
        val finalPath = interestPath.ifBlank { "관심사" }
        val recommendedKeywords = clovaApiService.getRecommendedInterests(finalPath, userInfo)

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
                    parentId = null,  // 배열 기반이므로 parentId는 사용하지 않음
                    depth = (interests?.size ?: 0) + 1,  // 현재 경로 깊이 + 1
                    interestName = keyword,
                    normalizedKey = null,
                    createdSource = com.haruUp.interest.domain.CreatedSourceType.AI
                )
            }.take(5)
        }
    }

    /**
     * 사용자 프로필 정보로부터 프롬프트용 문자열 생성
     * @return 사용자 정보 문자열 (예: "나이: 30세, 성별: 남성, 자기소개: 개발자")
     */
    private fun buildUserInfo(): String? {
        try {
            val memberId = securityUtils.getCurrentMemberId() ?: return null
            val profile = memberProfileRepository.findByMemberId(memberId) ?: return null

            val userInfoParts = mutableListOf<String>()

            // 나이 정보 추가
            profile.birthDt?.let { birthDt ->
                val age = calculateAge(birthDt)
                if (age > 0) userInfoParts.add("나이: ${age}세")
            }

            // 성별 정보 추가
            profile.gender?.let { gender ->
                val genderText = when (gender) {
                    MemberGender.MALE -> "남성"
                    MemberGender.FEMALE -> "여성"
                    MemberGender.OTHER -> "기타"
                }
                userInfoParts.add("성별: $genderText")
            }

            // 자기소개 정보 추가
            profile.intro?.takeIf { it.isNotBlank() }?.let { intro ->
                userInfoParts.add("자기소개: $intro")
            }

            return if (userInfoParts.isNotEmpty()) {
                userInfoParts.joinToString(", ")
            } else null
        } catch (e: Exception) {
            logger.error("사용자 정보 조회 실패", e)
            return null
        }
    }

    /**
     * 생년월일로부터 나이 계산
     * @param birthDt 생년월일
     * @return 만 나이
     */
    private fun calculateAge(birthDt: LocalDateTime): Int {
        val birthDate = birthDt.toLocalDate()
        val today = LocalDateTime.now().toLocalDate()
        return Period.between(birthDate, today).years
    }
}
