/**
 * 기존 관심사&목표 기반 미션추천 테스트
 *
 * /api/missions/recommend 에서 사용하는 방식:
 * 관심사(directFullPath) + 멤버 프로필 기반으로
 * Clova / Claude 각각에게 미션 추천을 요청하고 결과를 비교
 *
 * 프롬프트: MissionRecommendationService.buildPrompt 로직 재현
 */

import { SYSTEM_PROMPT, INTEREST_TEST_SCENARIOS, validateMissionResponse } from "./config.js";
import { clovaGenerateText } from "./clova-client.js";
import { claudeGenerateText } from "./claude-client.js";

/**
 * 관심사 + 멤버 프로필 → 사용자 메시지 생성
 * (MissionRecommendationService.buildPrompt 재현)
 */
function buildUserPrompt(scenario) {
  const profile = scenario.memberProfile;
  const profileParts = [];

  if (profile.age) profileParts.push(`${profile.age}세`);
  if (profile.gender) {
    profileParts.push(profile.gender === "MALE" ? "남성" : "여성");
  }
  if (profile.jobName) profileParts.push(`직업: ${profile.jobName}`);
  if (profile.jobDetailName) profileParts.push(`직업상세: ${profile.jobDetailName}`);

  const pathStr = scenario.interests.join(" > ");
  const jsonExample = [1, 2, 3, 4, 5]
    .map((d) => `{"content": "미션내용", "relatedInterest": ["대분류", "중분류", "소분류"], "difficulty": ${d}}`)
    .join(",\n    ");

  return `사용자 정보: ${profileParts.join(", ")}
관심사: [${pathStr}]

===== 생성 요청 =====
난이도 1, 2, 3, 4, 5 각각 1개씩, 총 5개의 미션을 생성하세요.

===== 난이도 기준 (하루 안에 완료 가능해야 함) =====
- 난이도 1 (초등학생): 5-10분, 아주 간단한 활동
- 난이도 2 (중학생): 15-30분, 기본적인 노력 필요
- 난이도 3 (고등학생): 30분-1시간, 집중력과 계획 필요
- 난이도 4 (대학생): 1-2시간, 전문 지식/기술 필요
- 난이도 5 (직장인): 2-3시간, 높은 전문성 필요

===== 좋은 미션 (필수) =====
✅ 구체적이고 측정 가능 (횟수, 시간, 개수 등 수치 포함)
✅ 하루 안에 완료 가능
예: "영어 단어 20개 암기", "스쿼트 3세트×15회", "책 50페이지 읽기"

===== 나쁜 미션 (금지) =====
❌ 모호함: "운동하기", "공부하기"
❌ 장기 목표: "한 달간 다이어트"
❌ 일회성: "헬스장 등록하기"
❌ 측정 불가: "건강해지기"

===== 응답 형식 (JSON만 출력) =====
\`\`\`json
{
  "missions": [
    ${jsonExample}
  ]
}
\`\`\``;
}

/**
 * 단일 시나리오 테스트 실행
 */
async function runScenario(scenario, provider) {
  const userMessage = buildUserPrompt(scenario);

  console.log(`\n${"─".repeat(60)}`);
  console.log(`📋 시나리오: ${scenario.name}`);
  console.log(`🤖 Provider: ${provider.toUpperCase()}`);
  console.log(`${"─".repeat(60)}`);

  try {
    let result;
    if (provider === "clova") {
      result = await clovaGenerateText(userMessage, SYSTEM_PROMPT);
    } else {
      result = await claudeGenerateText(userMessage, SYSTEM_PROMPT);
    }

    console.log(`⏱  응답시간: ${result.elapsed}ms`);
    console.log(`[Raw Response]\n${result.content}\n`);

    const validation = validateMissionResponse(result.content);
    if (validation.valid) {
      console.log("✅ 검증 통과");
      console.log("[추천 미션]");
      for (const m of validation.parsed.missions) {
        console.log(`  난이도${m.difficulty}: ${m.content} (${m.relatedInterest.join(" > ")})`);
      }
    } else {
      console.log("❌ 검증 실패:");
      validation.errors.forEach((e) => console.log(`   - ${e}`));
      if (validation.parsed?.missions) {
        console.log("[파싱된 미션]");
        for (const m of validation.parsed.missions) {
          console.log(`  난이도${m.difficulty}: ${m.content}`);
        }
      }
    }

    return {
      scenario: scenario.name,
      provider,
      elapsed: result.elapsed,
      valid: validation.valid,
      errors: validation.errors,
      missions: validation.parsed?.missions || [],
    };
  } catch (err) {
    console.log(`💥 에러: ${err.message}`);
    return {
      scenario: scenario.name,
      provider,
      elapsed: 0,
      valid: false,
      errors: [err.message],
      missions: [],
    };
  }
}

/**
 * 관심사 기반 미션추천 테스트 실행
 */
export async function runInterestTest(provider) {
  console.log(`\n${"═".repeat(60)}`);
  console.log(`🧪 관심사 기반 미션추천 테스트 (${provider.toUpperCase()})`);
  console.log(`${"═".repeat(60)}`);

  const results = [];
  for (const scenario of INTEREST_TEST_SCENARIOS) {
    const result = await runScenario(scenario, provider);
    results.push(result);
  }

  return results;
}
