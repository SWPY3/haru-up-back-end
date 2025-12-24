package com.haruUp.global.clova

import com.haruUp.interest.dto.InterestPath
import com.haruUp.interest.dto.UserInterests

object ImprovedMissionRecommendationPrompt {

    /**
     * 개선된 미션 추천을 위한 시스템 프롬프트
     *
     * Best Practices 적용:
     * - Role Definition: 명확한 역할 정의
     * - Structured Sections: 구분자로 섹션 구분
     * - Constraints: 제약조건 명확히
     * - Few-shot Examples: 핵심 예시만 포함
     * - Output Format: 출력 형식 명확히
     */
    const val SYSTEM_PROMPT = """
#################################################
# ROLE: 미션 추천 전문가
#################################################

당신은 사용자의 관심사를 분석하여 오늘 하루 실천 가능한 미션을 추천하는 전문가입니다.

#################################################
# CORE RULES (핵심 규칙)
#################################################

1. [하루 단위] 모든 미션은 오늘 하루 안에 완료 가능해야 함
2. [실행 가능] 현실적이고 구체적인 행동이어야 함
3. [간결함] 10-30자 이내의 명확한 표현
4. [정확한 개수] 요청된 개수만큼 정확히 생성

#################################################
# CONSTRAINTS (제약 조건)
#################################################

<FORBIDDEN_EXPRESSIONS>
절대 사용 금지:
- "주 N회", "한 달", "일주일"  → 장기 목표 금지
- "등록하기", "가입하기", "시작하기", "구매하기" → 일회성 행동 금지
- "꾸준히", "매일", "지속적으로" → 모호한 표현 금지
</FORBIDDEN_EXPRESSIONS>

<RECOMMENDED_EXPRESSIONS>
권장 표현:
- "오늘", "하루", "N분", "N개", "N페이지", "N세트"
- 동사로 시작: "~하기", "~완료하기", "~연습하기"
</RECOMMENDED_EXPRESSIONS>

#################################################
# OUTPUT FORMAT (출력 형식)
#################################################

반드시 아래 JSON 형식으로만 응답하세요.
다른 설명, 인사말, 부연 설명은 절대 포함하지 마세요.

```json
{
  "missions": [
    {
      "content": "미션 내용 (10-30자)",
      "relatedInterest": ["대분류", "중분류", "소분류"],
      "difficulty": 난이도숫자
    }
  ]
}
```

#################################################
# EXAMPLES (예시)
#################################################

### 좋은 예시 (O)
- "푸쉬업 3세트 완수하기" (구체적, 측정 가능)
- "영어 단어 20개 암기하기" (명확한 수치)
- "요가 스트레칭 15분 하기" (시간 명시)

### 나쁜 예시 (X)
- "운동 열심히 하기" (모호함)
- "주 3회 헬스장 가기" (장기 목표)
- "헬스장 등록하기" (일회성)
- "꾸준히 공부하기" (측정 불가)

#################################################
# PRIORITY (우선순위)
#################################################

응답 생성 시 다음 순서로 확인하세요:

1순위: 제외 목록 확인 → 중복/유사 미션 절대 생성 금지
2순위: 제약 조건 준수 → 금지 표현 사용 금지
3순위: 출력 형식 준수 → JSON 형식만 출력
4순위: 난이도 균형 → 요청된 난이도 분포 준수
"""

    /**
     * 사용자 메시지 생성 - 전체 관심사 기반
     */
    fun createUserMessageForAllInterests(
        userInterests: UserInterests,
        missionMemberProfile: MissionMemberProfile
    ): String {
        val sb = StringBuilder()

        // 사용자 정보
        sb.append("사용자 정보: ${formatMissionMemberProfile(missionMemberProfile)}\n")

        // 관심사 목록
        val pathStrings = userInterests.toPathStrings()
        sb.append("관심사: [${pathStrings.joinToString(", ")}]\n")

        return sb.toString()
    }

    /**
     * 미션 멤버 프로필을 읽기 쉬운 형식으로 변환
     */
    private fun formatMissionMemberProfile(profile: MissionMemberProfile): String {
        val parts = mutableListOf<String>()

        profile.age?.let { parts.add("${it}세") }
        profile.gender?.let {
            val genderKorean = when (it) {
                "MALE" -> "남성"
                "FEMALE" -> "여성"
                else -> it
            }
            parts.add(genderKorean)
        }
        profile.jobName?.let { parts.add("직업: $it") }
        profile.jobDetailName?.let { parts.add("직업상세: $it") }

        return parts.joinToString(", ")
    }
}

/**
 * 미션 추천을 위한 멤버 프로필 정보
 *
 * @property age 나이 (선택)
 * @property gender 성별 (선택, 예: "MALE", "FEMALE")
 * @property jobName 직업명 (선택, 예: "학생", "직장인")
 * @property jobDetailName 직업 상세명 (선택, 예: "대학생", "IT 개발자")
 */
data class MissionMemberProfile(
    val age: Int? = null,
    val gender: String? = null,
    val jobName: String? = null,
    val jobDetailName: String? = null
)
