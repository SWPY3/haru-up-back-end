/**
 * 챗봇 기반 미션추천 테스트
 *
 * 챗봇 대화에서 수집한 컨텍스트(목표, 실력, 경험 등)를 기반으로
 * Clova / Claude 각각에게 미션 추천을 요청하고 결과를 비교
 *
 * 프롬프트: ChatBotMissionRecommendationService.buildAdditionalContext +
 *          MissionRecommendationService.buildPrompt (38d4657 커밋 기준) 완전 재현
 */

import { SYSTEM_PROMPT, CHATBOT_TEST_SCENARIOS, validateMissionResponse } from "./config.js";
import { clovaGenerateText } from "./clova-client.js";
import { claudeGenerateText } from "./claude-client.js";

/**
 * 시간 파싱 (ChatBotMissionRecommendationService.extractDailyTimeBudgetMinutes 재현)
 */
function extractMinutes(input) {
  const m1 = input.match(/(\d+)\s*시간(?:\s*(\d+)\s*분)?/);
  if (m1) return parseInt(m1[1]) * 60 + (parseInt(m1[2]) || 0);
  const m2 = input.match(/(\d+)\s*분/);
  if (m2) return parseInt(m2[1]);
  return null;
}

/**
 * additionalContext 생성 (ChatBotMissionRecommendationService.buildAdditionalContext 완전 재현)
 */
function buildAdditionalContext(ctx) {
  const lines = [
    `현재 목표: ${ctx.goal}`,
    `최종 결과물: ${ctx.desiredOutcome}`,
    `현재 실력: ${ctx.skillLevel}`,
    `최근 직접 해본 작업: ${ctx.recentExperience}`,
    `목표 기간: ${ctx.targetPeriod}`,
    `하루 투자 가능 시간: ${ctx.dailyAvailableTime}`,
    `미션 원칙: 하루에 한 번 끝낼 수 있는 단일 작업만 추천`,
  ];
  const minutes = extractMinutes(ctx.dailyAvailableTime);
  if (minutes) lines.push(`하루 미션 시간 상한: 약 ${minutes}분 이내`);
  if (ctx.additionalOpinion) lines.push(`추가 의견: ${ctx.additionalOpinion}`);

  // completedMissions: 다음날 후속 추천 모드
  if (ctx.completedMissions && ctx.completedMissions.length > 0) {
    lines.push("이번 추천 모드: 다음날 후속 추천");
    lines.push("전날 완료한 미션:");
    ctx.completedMissions
      .sort((a, b) => a.difficulty - b.difficulty)
      .forEach((m) => {
        lines.push(`- 난이도 ${m.difficulty}: ${m.content}`);
      });
    lines.push("위 미션은 모두 완료된 상태입니다.");
    lines.push("같은 난이도라도 전날 미션보다 한 단계 더 진전된 후속 미션으로 추천하세요.");
    lines.push("전날과 동일하거나 거의 같은 미션은 다시 추천하지 마세요.");
    lines.push("전날보다 더 적용형, 더 실전형, 더 구체적인 결과물이 나오도록 추천하세요.");
  }

  return lines.join("\n");
}

/**
 * 챗봇 미션추천용 유저 프롬프트 생성
 * (MissionRecommendationService.buildPrompt 38d4657 커밋 기준 완전 재현)
 */
function buildUserPrompt(ctx) {
  const additionalContext = buildAdditionalContext(ctx);
  const minutes = extractMinutes(ctx.dailyAvailableTime);
  const jsonExample = [1, 2, 3, 4, 5]
    .map((d) => `{"content": "미션내용", "relatedInterest": ["대분류", "중분류"], "difficulty": ${d}}`)
    .join(",\n    ");

  let prompt = `사용자 정보: 직업: ${ctx.category}, 직업상세: ${ctx.subCategory}
관심사: [${ctx.category} > ${ctx.subCategory}]

===== 생성 요청 =====
난이도 1, 2, 3, 4, 5 각각 1개씩, 총 5개의 미션을 생성하세요.

===== 추가 사용자 문맥 =====
${additionalContext}

위 문맥을 반드시 반영해서 미션을 생성하세요.
- 현재 실력에 맞게 현실적인 수준으로 제안하세요.
- 하루 투자 가능 시간을 크게 넘지 않도록 하세요.
- 목표 기간 안에 도달할 수 있는 단계형 미션으로 제안하세요.
`;

  // 시간 예산 섹션 (buildTimeBudgetSection 재현)
  if (minutes) {
    prompt += `\n===== 시간 예산 엄수 =====
- 사용자의 하루 미션 시간 상한은 약 ${minutes}분입니다.
- 난이도 5도 이 시간 안에서 끝나야 합니다.
- 한 번에 산출물 1개만 요구하세요.`;
    if (minutes <= 60) {
      prompt += `
- 60분 이하 사용자에게는 여러 곳 비교, SWOT 분석, 페르소나 여러 개 상세 작성, 화면 여러 개 와이어프레임을 제안하지 마세요.
- 난이도 1은 5~10분, 난이도 2는 10~20분, 난이도 3은 20~35분, 난이도 4는 35~50분, 난이도 5는 50~60분 안에 끝나는 작업으로 제안하세요.
- 예: "경쟁사 1곳 핵심 기능 3개 메모", "화면 1개 손그림 와이어프레임", "페르소나 1명 pain point 3개 적기"`;
    } else if (minutes <= 90) {
      prompt += `
- 90분 이하 사용자에게는 프로젝트 단계 전체나 문서 묶음 작업을 제안하지 마세요.
- 화면, 문서, 시나리오는 1개씩만 제안하는 편이 좋습니다.`;
    }
    prompt += "\n";
  }

  prompt += `
===== 난이도 기준 (모든 난이도는 하루 안에 완료 가능해야 함) =====
- 난이도 1: 오늘 바로 시작할 수 있는 아주 작은 행동 1개
- 난이도 2: 짧은 실습 또는 예제 수행 1개
- 난이도 3: 집중해서 끝낼 수 있는 중간 크기 작업 1개
- 난이도 4: 다소 도전적이지만 하루 안에 닫히는 단일 작업 1개
- 난이도 5: 그날 할 수 있는 가장 어려운 작업이지만, 여전히 하루 투자 가능 시간 안에서 끝나는 작업 1개

===== 범위 제한 (매우 중요) =====
- 각 미션은 "오늘 할 1개 행동"이어야 합니다.
- 프로젝트 전체를 끝내는 표현을 절대 쓰지 마세요.
- 난이도 5도 기능 묶음이나 프로젝트 단계 전체가 아니라, 하루 안에 검증 가능한 단일 과업이어야 합니다.
- "구현", "연동", "테스트", "설계", "구성" 같은 표현을 쓸 때는 반드시 범위를 좁히세요.
- 숫자, 개수, 시간, 화면 수, 사람 수 등으로 범위를 드러내세요.
- 여러 산출물을 한 번에 요구하지 마세요.
- 분석 후 작성, 조사 후 정리처럼 큰 2단계 묶음 작업은 피하세요.
- 문장 안에서 '후', '및', '+', '&', '그리고'를 사용해 여러 행동을 묶지 마세요.

===== 좋은 미션 (필수) =====
✅ 구체적이고 측정 가능 (횟수, 시간, 개수 등 수치 포함)
✅ 하루 안에 완료 가능
예: "영어 단어 20개 암기", "스쿼트 3세트×15회", "책 50페이지 읽기"
예: "지인 1명에게 화면 3장 보여주고 피드백 3개 메모"
예: "Firebase 인증 예제 1개 따라 하고 로그인 성공 화면 캡처"

===== 나쁜 미션 (금지) =====
❌ 모호함: "운동하기", "공부하기"
❌ 장기 목표: "한 달간 다이어트"
❌ 일회성: "헬스장 등록하기"
❌ 측정 불가: "건강해지기"
❌ 범위 과다: "MVP 핵심 기능 검증용 사용자 테스트 진행"
❌ 범위 과다: "전체 프로젝트 설계서 작성 및 CI/CD 파이프라인 구성"
❌ 범위 과다: "Firebase 연동하여 실시간 DB 저장/조회 기능 구현"

===== 응답 형식 (JSON만 출력) =====
\`\`\`json
{
  "missions": [
    ${jsonExample}
  ]
}
\`\`\``;

  return prompt.trim();
}

/**
 * 단일 시나리오 테스트 실행
 */
async function runScenario(scenario, provider) {
  const userMessage = buildUserPrompt(scenario.context);

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
      context: scenario.context,
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
      context: scenario.context,
    };
  }
}

/**
 * 챗봇 미션추천 테스트 실행
 */
export async function runChatbotTest(provider) {
  console.log(`\n${"═".repeat(60)}`);
  console.log(`🧪 챗봇 기반 미션추천 테스트 (${provider.toUpperCase()})`);
  console.log(`${"═".repeat(60)}`);

  const results = [];
  for (const scenario of CHATBOT_TEST_SCENARIOS) {
    const result = await runScenario(scenario, provider);
    results.push(result);
  }

  return results;
}
