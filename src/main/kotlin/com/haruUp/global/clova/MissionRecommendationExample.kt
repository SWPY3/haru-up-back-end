package com.haruUp.global.clova

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

/**
 * 미션 추천 사용 예시
 *
 * 이 클래스는 ClovaApiClient를 사용하여 사용자 관심사 기반 미션을 추천받는 방법을 보여줍니다.
 */
class MissionRecommendationExample(
    private val clovaApiClient: ClovaApiClient
) {
    private val objectMapper = jacksonObjectMapper()

    /**
     * 미션 추천 받기
     *
     * @param mainCategory 대분류 (예: "운동")
     * @param middleCategory 중분류 (예: "헬스")
     * @param subCategory 소분류 (예: "웨이트 트레이닝")
     * @param missionUserProfile 사용자 프로필 정보
     * @return 추천된 미션 리스트 (5개)
     */
    fun recommendMissions(
        mainCategory: String,
        middleCategory: String,
        subCategory: String,
        missionUserProfile: MissionUserProfile
    ): List<String> {
        val userMessage = MissionRecommendationPrompt.createUserMessageForMission(
            mainCategory = mainCategory,
            middleCategory = middleCategory,
            subCategory = subCategory,
            missionUserProfile = missionUserProfile
        )

        val response = clovaApiClient.generateText(
            userMessage = userMessage,
            systemMessage = MissionRecommendationPrompt.SYSTEM_PROMPT
        )

        return parseMissions(response)
    }

    /**
     * JSON 객체 형식의 응답을 파싱하여 미션 리스트로 변환
     */
    private fun parseMissions(jsonResponse: String): List<String> {
        return try {
            val response = objectMapper.readValue<MissionResponse>(jsonResponse.trim())
            response.missions
        } catch (e: Exception) {
            // JSON 파싱 실패 시 예외 처리
            throw RuntimeException("AI 미션 응답을 파싱하는데 실패했습니다: ${e.message}", e)
        }
    }

    /**
     * AI 응답을 위한 데이터 클래스
     */
    private data class MissionResponse(
        val missions: List<String>
    )
}

/**
 * 사용 예시:
 *
 * // 1. 사용자 프로필 생성
 * val userProfile = MissionUserProfile(
 *     age = 28,
 *     introduction = "직장인, 운동 시작한지 3개월"
 * )
 *
 * // 2. 미션 추천 받기
 * val missions = recommendMissions(
 *     mainCategory = "운동",
 *     middleCategory = "헬스",
 *     subCategory = "웨이트 트레이닝",
 *     missionUserProfile = userProfile
 * )
 * // 결과 예시:
 * // [
 * //   "주 3회 헬스장 방문하기",
 * //   "스쿼트 10kg 증량 도전하기",
 * //   "벤치프레스 정확한 자세로 3세트 완수하기",
 * //   "운동 루틴 일지 작성하기",
 * //   "트레이너에게 자세 교정 받기"
 * // ]
 *
 * // 3. 다양한 관심사로 미션 추천
 * val musicProfile = MissionUserProfile(
 *     age = 22,
 *     introduction = "대학생, 음악 듣는 것을 좋아함"
 * )
 *
 * val musicMissions = recommendMissions(
 *     mainCategory = "취미",
 *     middleCategory = "음악 감상",
 *     subCategory = "인디음악",
 *     missionUserProfile = musicProfile
 * )
 * // 결과 예시:
 * // [
 * //   "매일 새로운 인디 아티스트 1명씩 발견하기",
 * //   "인디 음악 플레이리스트 만들기",
 * //   "라이브 공연 1회 관람하기",
 * //   "좋아하는 곡 5곡 선정하고 감상평 작성하기",
 * //   "인디 음악 커뮤니티 가입하고 추천 받기"
 * // ]
 *
 * // 4. 최소한의 정보만 제공하는 경우
 * val minimalProfile = MissionUserProfile(age = 25)
 * val minimalMissions = recommendMissions(
 *     mainCategory = "학습",
 *     middleCategory = "외국어",
 *     subCategory = "영어회화",
 *     missionUserProfile = minimalProfile
 * )
 * // AI가 나이 정보만으로도 적절한 미션을 추천합니다
 *
 * // 5. 소개를 상세히 작성한 경우
 * val detailedProfile = MissionUserProfile(
 *     age = 35,
 *     introduction = "주부, 가족을 위해 베이킹 배우고 싶음, 오븐은 있음"
 * )
 * val detailedMissions = recommendMissions(
 *     mainCategory = "요리",
 *     middleCategory = "베이킹",
 *     subCategory = "케이크 만들기",
 *     missionUserProfile = detailedProfile
 * )
 * // 더 구체적이고 개인화된 미션이 추천됩니다
 */
