#!/usr/bin/env node

/**
 * 방법 1 테스트: 목표 입력 → LLM 맞춤 질문 → 미션 추천
 *
 * 흐름:
 *   [1] 사용자: 목표 입력
 *   [2] LLM: 맞춤 후속 질문 1~2개 생성
 *   [3] 사용자: 후속 질문 답변 + 하루 시간/기간
 *   [4] LLM: 미션 추천
 *
 * Clova vs Claude 품질 비교
 */

import fs from "fs";
import path from "path";
import { fileURLToPath } from "url";
import { SYSTEM_PROMPT, validateMissionResponse } from "./config.js";
import { clovaGenerateText } from "./clova-client.js";
import { claudeGenerateText } from "./claude-client.js";

const __dirname = path.dirname(fileURLToPath(import.meta.url));

// ── 테스트 시나리오 ──
// goal만 입력 → LLM이 후속 질문 → 미리 준비한 답변으로 응답
const SCENARIOS = [
  {
    name: "토익 900점",
    goal: "토익 900점 달성하고 싶어요",
    followUpAnswers: {
      현재점수: "지금 750점이에요",
      약한부분: "LC 파트3,4가 특히 약해요",
      공부경험: "모의고사를 몇 번 풀어봤는데 시간이 부족했어요",
    },
    dailyTime: "1시간 30분",
    targetPeriod: "3개월",
  },
  {
    name: "체중 5kg 감량",
    goal: "체중 5kg 빼고 싶어요",
    followUpAnswers: {
      현재상태: "현재 75kg이고 목표는 70kg이에요",
      운동경험: "운동 경험 거의 없고, 일주일 저녁 안 먹어봤는데 3일 만에 포기했어요",
      제약사항: "점심은 회사 구내식당이라 조절이 어려워요",
    },
    dailyTime: "40분",
    targetPeriod: "3개월",
  },
  {
    name: "기타로 노래 반주",
    goal: "기타 쳐서 노래 반주하고 싶어요",
    followUpAnswers: {
      현재수준: "완전 초보예요, 기타를 막 샀어요",
      목표곡: "좋아하는 노래 5곡 정도 치고 싶어요",
      경험: "유튜브로 코드 잡는 영상 하나 봤어요",
    },
    dailyTime: "30분",
    targetPeriod: "4개월",
  },
  {
    name: "일본어 JLPT N3",
    goal: "JLPT N3 합격하고 싶어요",
    followUpAnswers: {
      현재수준: "히라가나/가타카나만 읽을 수 있고 문법은 거의 몰라요",
      어려운점: "한자가 너무 어려워서 걱정이에요",
      경험: "애니메이션으로 인사말 몇 개 외운 정도예요",
    },
    dailyTime: "40분",
    targetPeriod: "8개월",
  },
  {
    name: "마라톤 10km 완주",
    goal: "10km 마라톤 완주하고 싶어요",
    followUpAnswers: {
      현재체력: "평소 운동 안 하고 2km만 뛰어도 힘들어요",
      제약사항: "무릎이 약한 편이라 부상이 걱정돼요",
      경험: "동네 공원에서 1.5km 조깅하다 포기했어요",
    },
    dailyTime: "40분",
    targetPeriod: "5개월",
  },
  {
    name: "매일 명상 습관",
    goal: "매일 명상해서 스트레스 관리하고 싶어요",
    followUpAnswers: {
      현재상태: "대학원생인데 논문 스트레스가 심해요",
      경험: "수면 전 심호흡 5분 해봤는데 잡생각이 많았어요",
      목표: "스트레스를 스스로 컨트롤할 수 있으면 좋겠어요",
    },
    dailyTime: "15분",
    targetPeriod: "2개월",
  },
];

// ── Step 1: LLM에게 후속 질문 생성 요청 ──
const QUESTION_PROMPT = `당신은 사용자의 목표를 구체화하기 위한 질문을 만드는 AI입니다.

사용자가 목표를 입력하면, 좋은 미션(하루 단위 실천 과제)을 추천하기 위해 꼭 필요한 후속 질문을 1~2개만 만들어주세요.

【규칙】
1. 질문은 최대 2개
2. 현재 수준, 어려운 점, 구체적 상황 등을 파악하는 질문
3. JSON만 출력

【출력 형식】
{"questions":["질문1","질문2"]}`;

// ── Step 2: 수집된 정보로 미션 추천 ──
function buildMissionPrompt(scenario, questions, answers) {
  const minutes = parseMinutes(scenario.dailyTime);
  const jsonEx = [1, 2, 3, 4, 5]
    .map((d) => `{"content": "미션내용", "relatedInterest": ["분류1", "분류2"], "difficulty": ${d}}`)
    .join(",\n    ");

  // Q&A 컨텍스트 구성
  let qaContext = "";
  if (questions && questions.length > 0) {
    const answerValues = Object.values(answers);
    qaContext = questions
      .map((q, i) => `Q: ${q}\nA: ${answerValues[i] || "답변 없음"}`)
      .join("\n");
  }

  let prompt = `목표: ${scenario.goal}
하루 투자 가능 시간: ${scenario.dailyTime}
목표 기간: ${scenario.targetPeriod}
`;

  if (qaContext) {
    prompt += `\n===== 사용자 추가 정보 =====\n${qaContext}\n`;
  }

  prompt += `
위 정보를 기반으로 난이도 1~5 각각 1개씩, 총 5개 미션을 추천하세요.
`;

  if (minutes) {
    prompt += `\n===== 시간 예산 =====
- 하루 미션 시간 상한: 약 ${minutes}분
- 난이도 5도 이 시간 안에서 끝나야 합니다.
`;
    if (minutes <= 60) {
      prompt += `- 난이도 1은 5~10분, 난이도 2는 10~20분, 난이도 3은 20~35분, 난이도 4는 35~50분, 난이도 5는 50~60분\n`;
    }
  }

  prompt += `
===== 난이도 기준 (하루 안에 완료) =====
- 난이도 1: 바로 시작할 수 있는 아주 작은 행동 1개
- 난이도 2: 짧은 실습 1개
- 난이도 3: 집중해서 끝낼 수 있는 중간 작업 1개
- 난이도 4: 도전적이지만 하루 안에 닫히는 작업 1개
- 난이도 5: 하루 시간 안에서 가장 어려운 작업 1개

===== 좋은 미션 =====
✅ 구체적, 측정 가능 (횟수, 시간, 개수)
✅ 하루 안에 완료 가능

===== 나쁜 미션 (금지) =====
❌ 모호: "운동하기"
❌ 장기: "한 달간 다이어트"
❌ 범위 과다: "전체 프로젝트 완성"

===== JSON만 출력 =====
\`\`\`json
{
  "missions": [
    ${jsonEx}
  ]
}
\`\`\``;

  return prompt.trim();
}

function parseMinutes(input) {
  const m1 = input.match(/(\d+)\s*시간(?:\s*(\d+)\s*분)?/);
  if (m1) return parseInt(m1[1]) * 60 + (parseInt(m1[2]) || 0);
  const m2 = input.match(/(\d+)\s*분/);
  if (m2) return parseInt(m2[1]);
  return null;
}

function parseQuestions(content) {
  try {
    const cleaned = content.replace(/```json?\s*/g, "").replace(/```/g, "").trim();
    const match = cleaned.match(/\{[\s\S]*"questions"[\s\S]*\}/);
    const parsed = JSON.parse(match ? match[0] : cleaned);
    return parsed.questions || [];
  } catch {
    return [];
  }
}

// ── 단일 시나리오 실행 ──
async function runScenario(scenario, provider) {
  const generate = provider === "clova" ? clovaGenerateText : claudeGenerateText;

  console.log(`\n${"─".repeat(60)}`);
  console.log(`📋 ${scenario.name} | ${provider.toUpperCase()}`);
  console.log(`${"─".repeat(60)}`);
  console.log(`[목표] ${scenario.goal}`);

  // Step 1: 후속 질문 생성
  const step1Start = Date.now();
  const qResult = await generate(
    `사용자 목표: "${scenario.goal}"`,
    QUESTION_PROMPT
  );
  const step1Elapsed = Date.now() - step1Start;

  const questions = parseQuestions(qResult.content);
  console.log(`[Step1] 후속 질문 생성 (${step1Elapsed}ms):`);
  questions.forEach((q, i) => console.log(`  Q${i + 1}: ${q}`));

  // Step 2: 미션 추천
  const missionPrompt = buildMissionPrompt(scenario, questions, scenario.followUpAnswers);
  const step2Start = Date.now();
  const mResult = await generate(missionPrompt, SYSTEM_PROMPT);
  const step2Elapsed = Date.now() - step2Start;
  const totalElapsed = step1Elapsed + step2Elapsed;

  console.log(`[Step2] 미션 추천 (${step2Elapsed}ms):`);

  const validation = validateMissionResponse(mResult.content);
  if (validation.valid) {
    console.log("✅ 검증 통과");
    for (const m of validation.parsed.missions) {
      console.log(`  난이도${m.difficulty}: ${m.content}`);
    }
  } else {
    console.log("❌ 검증 실패:", validation.errors.join(", "));
  }

  console.log(`⏱  총 소요: ${totalElapsed}ms (질문 ${step1Elapsed}ms + 미션 ${step2Elapsed}ms)`);

  return {
    scenario: scenario.name,
    provider,
    goal: scenario.goal,
    questions,
    followUpAnswers: scenario.followUpAnswers,
    dailyTime: scenario.dailyTime,
    targetPeriod: scenario.targetPeriod,
    missions: validation.parsed?.missions || [],
    valid: validation.valid,
    errors: validation.errors,
    step1Elapsed,
    step2Elapsed,
    totalElapsed,
  };
}

// ── MD 생성 ──
function generateMarkdown(provider, results, timestamp) {
  const passed = results.filter((r) => r.valid).length;
  const avgTotal = Math.round(results.reduce((s, r) => s + r.totalElapsed, 0) / results.length);
  const avgStep1 = Math.round(results.reduce((s, r) => s + r.step1Elapsed, 0) / results.length);
  const avgStep2 = Math.round(results.reduce((s, r) => s + r.step2Elapsed, 0) / results.length);

  let md = `# ${provider.toUpperCase()} 방법1 테스트: 목표 → 맞춤질문 → 미션추천\n\n`;
  md += `- 실행일시: ${timestamp.replace("_", " ")}\n`;
  md += `- Provider: **${provider.toUpperCase()}**\n`;
  md += `- 검증 통과: **${passed}/${results.length}**\n`;
  md += `- 평균 소요: **${avgTotal}ms** (질문 ${avgStep1}ms + 미션 ${avgStep2}ms)\n\n---\n\n`;

  for (const r of results) {
    md += `## ${r.valid ? "PASS" : "FAIL"} | ${r.scenario} (${r.totalElapsed}ms)\n\n`;

    md += `| 항목 | 내용 |\n|------|------|\n`;
    md += `| 목표 | ${r.goal} |\n`;
    md += `| 하루 시간 | ${r.dailyTime} |\n`;
    md += `| 목표 기간 | ${r.targetPeriod} |\n\n`;

    if (r.questions.length > 0) {
      md += `**LLM 후속 질문:**\n`;
      const answerValues = Object.values(r.followUpAnswers);
      r.questions.forEach((q, i) => {
        md += `- Q: ${q}\n`;
        md += `  - A: ${answerValues[i] || "-"}\n`;
      });
      md += `\n`;
    }

    if (r.missions.length > 0) {
      md += `| 난이도 | 미션 내용 |\n|--------|----------|\n`;
      for (const m of r.missions.sort((a, b) => a.difficulty - b.difficulty)) {
        md += `| ${m.difficulty} | ${m.content} |\n`;
      }
      md += `\n`;
    }

    if (!r.valid) md += `**오류:** ${r.errors.join(", ")}\n\n`;
  }

  return md;
}

// ── 메인 ──
async function main() {
  const now = new Date();
  const pad = (n) => String(n).padStart(2, "0");
  const ts = `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}_${pad(now.getHours())}-${pad(now.getMinutes())}-${pad(now.getSeconds())}`;

  const allResults = [];

  for (const provider of ["clova", "claude"]) {
    console.log(`\n${"═".repeat(60)}`);
    console.log(`🧪 방법1 테스트 (${provider.toUpperCase()})`);
    console.log(`${"═".repeat(60)}`);

    const results = [];
    for (const scenario of SCENARIOS) {
      const r = await runScenario(scenario, provider);
      results.push(r);
    }

    // 저장
    const dir = path.join(__dirname, "results", provider);
    fs.mkdirSync(dir, { recursive: true });
    const filename = `method1_${ts}.md`;
    fs.writeFileSync(path.join(dir, filename), generateMarkdown(provider, results, ts), "utf-8");
    console.log(`\n📁 결과 저장: results/${provider}/${filename}`);

    allResults.push({ provider, results });
  }

  // 요약
  console.log(`\n${"═".repeat(60)}`);
  console.log("📊 방법1 결과 요약");
  console.log(`${"═".repeat(60)}\n`);

  for (const { provider, results } of allResults) {
    const passed = results.filter((r) => r.valid).length;
    const avgTotal = Math.round(results.reduce((s, r) => s + r.totalElapsed, 0) / results.length);
    console.log(`[${provider.toUpperCase()}] 통과: ${passed}/${results.length} | 평균: ${avgTotal}ms`);
    for (const r of results) {
      console.log(`  ${r.valid ? "✅" : "❌"} ${r.scenario} (${r.totalElapsed}ms)`);
    }
    console.log();
  }
}

main().catch((err) => {
  console.error("💥", err);
  process.exit(1);
});
