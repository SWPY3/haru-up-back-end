#!/usr/bin/env node

/**
 * 구체적 목표 기반 미션추천 테스트
 * (토익 900점, 체중 감량, 수능 1등급 등)
 *
 * 사용법:
 *   node run-goals.js --all
 *   node run-goals.js --chatbot-clova
 *   node run-goals.js --chatbot-claude
 *   node run-goals.js --interest-clova
 *   node run-goals.js --interest-claude
 */

import fs from "fs";
import path from "path";
import { fileURLToPath } from "url";

// config-goals.js에서 시나리오를 가져옴
import {
  SYSTEM_PROMPT,
  CHATBOT_TEST_SCENARIOS,
  INTEREST_TEST_SCENARIOS,
  validateMissionResponse,
} from "./config-goals.js";

// 기존 테스트 로직 재사용 (동적 import 대신 직접 구현)
import { clovaGenerateText } from "./clova-client.js";
import { claudeGenerateText } from "./claude-client.js";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const args = process.argv.slice(2);

if (args.length === 0) {
  console.log(`
구체적 목표 기반 미션추천 테스트 (토익, 체중감량, 수능 등)
============================================================

사용법:
  node run-goals.js --all                # 전체 (4가지 모두)
  node run-goals.js --chatbot-clova
  node run-goals.js --chatbot-claude
  node run-goals.js --interest-clova
  node run-goals.js --interest-claude
`);
  process.exit(0);
}

// ── 시간 파싱 ──
function extractMinutes(input) {
  const m1 = input.match(/(\d+)\s*시간(?:\s*(\d+)\s*분)?/);
  if (m1) return parseInt(m1[1]) * 60 + (parseInt(m1[2]) || 0);
  const m2 = input.match(/(\d+)\s*분/);
  if (m2) return parseInt(m2[1]);
  return null;
}

// ── 챗봇 프롬프트 (38d4657 백엔드 기준 완전 재현) ──
function buildChatbotPrompt(ctx) {
  const minutes = extractMinutes(ctx.dailyAvailableTime);
  const ctxLines = [
    `현재 목표: ${ctx.goal}`,
    `최종 결과물: ${ctx.desiredOutcome}`,
    `현재 실력: ${ctx.skillLevel}`,
    `최근 직접 해본 작업: ${ctx.recentExperience}`,
    `목표 기간: ${ctx.targetPeriod}`,
    `하루 투자 가능 시간: ${ctx.dailyAvailableTime}`,
    `미션 원칙: 하루에 한 번 끝낼 수 있는 단일 작업만 추천`,
  ];
  if (minutes) ctxLines.push(`하루 미션 시간 상한: 약 ${minutes}분 이내`);
  if (ctx.additionalOpinion) ctxLines.push(`추가 의견: ${ctx.additionalOpinion}`);

  if (ctx.completedMissions && ctx.completedMissions.length > 0) {
    ctxLines.push("이번 추천 모드: 다음날 후속 추천");
    ctxLines.push("전날 완료한 미션:");
    ctx.completedMissions.sort((a, b) => a.difficulty - b.difficulty).forEach((m) => {
      ctxLines.push(`- 난이도 ${m.difficulty}: ${m.content}`);
    });
    ctxLines.push("위 미션은 모두 완료된 상태입니다.");
    ctxLines.push("같은 난이도라도 전날 미션보다 한 단계 더 진전된 후속 미션으로 추천하세요.");
    ctxLines.push("전날과 동일하거나 거의 같은 미션은 다시 추천하지 마세요.");
    ctxLines.push("전날보다 더 적용형, 더 실전형, 더 구체적인 결과물이 나오도록 추천하세요.");
  }

  const jsonEx = [1,2,3,4,5].map(d => `{"content": "미션내용", "relatedInterest": ["대분류", "중분류"], "difficulty": ${d}}`).join(",\n    ");

  let p = `사용자 정보: 직업: ${ctx.category}, 직업상세: ${ctx.subCategory}
관심사: [${ctx.category} > ${ctx.subCategory}]

===== 생성 요청 =====
난이도 1, 2, 3, 4, 5 각각 1개씩, 총 5개의 미션을 생성하세요.

===== 추가 사용자 문맥 =====
${ctxLines.join("\n")}

위 문맥을 반드시 반영해서 미션을 생성하세요.
- 현재 실력에 맞게 현실적인 수준으로 제안하세요.
- 하루 투자 가능 시간을 크게 넘지 않도록 하세요.
- 목표 기간 안에 도달할 수 있는 단계형 미션으로 제안하세요.
`;

  if (minutes) {
    p += `\n===== 시간 예산 엄수 =====
- 사용자의 하루 미션 시간 상한은 약 ${minutes}분입니다.
- 난이도 5도 이 시간 안에서 끝나야 합니다.
- 한 번에 산출물 1개만 요구하세요.`;
    if (minutes <= 60) {
      p += `
- 60분 이하 사용자에게는 여러 곳 비교, SWOT 분석, 페르소나 여러 개 상세 작성, 화면 여러 개 와이어프레임을 제안하지 마세요.
- 난이도 1은 5~10분, 난이도 2는 10~20분, 난이도 3은 20~35분, 난이도 4는 35~50분, 난이도 5는 50~60분 안에 끝나는 작업으로 제안하세요.
- 예: "경쟁사 1곳 핵심 기능 3개 메모", "화면 1개 손그림 와이어프레임", "페르소나 1명 pain point 3개 적기"`;
    } else if (minutes <= 90) {
      p += `
- 90분 이하 사용자에게는 프로젝트 단계 전체나 문서 묶음 작업을 제안하지 마세요.
- 화면, 문서, 시나리오는 1개씩만 제안하는 편이 좋습니다.`;
    }
    p += "\n";
  }

  p += `
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
    ${jsonEx}
  ]
}
\`\`\``;

  return p.trim();
}

// ── 관심사 프롬프트 (stash 기준) ──
function buildInterestPrompt(scenario) {
  const profile = scenario.memberProfile;
  const parts = [];
  if (profile.age) parts.push(`${profile.age}세`);
  if (profile.gender) parts.push(profile.gender === "MALE" ? "남성" : "여성");
  if (profile.jobName) parts.push(`직업: ${profile.jobName}`);
  if (profile.jobDetailName) parts.push(`직업상세: ${profile.jobDetailName}`);

  const pathStr = scenario.interests.join(" > ");
  const jsonEx = [1,2,3,4,5].map(d => `{"content": "미션내용", "relatedInterest": ["대분류", "중분류", "소분류"], "difficulty": ${d}}`).join(",\n    ");

  return `사용자 정보: ${parts.join(", ")}
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
    ${jsonEx}
  ]
}
\`\`\``;
}

// ── 테스트 실행 ──
async function runScenario(name, userMessage, provider, context) {
  console.log(`\n${"─".repeat(60)}`);
  console.log(`📋 ${name} | ${provider.toUpperCase()}`);
  console.log(`${"─".repeat(60)}`);

  try {
    const result = provider === "clova"
      ? await clovaGenerateText(userMessage, SYSTEM_PROMPT)
      : await claudeGenerateText(userMessage, SYSTEM_PROMPT);

    console.log(`⏱  ${result.elapsed}ms`);

    const v = validateMissionResponse(result.content);
    if (v.valid) {
      console.log("✅ 통과");
      for (const m of v.parsed.missions) console.log(`  난이도${m.difficulty}: ${m.content}`);
    } else {
      console.log("❌ 실패:", v.errors.join(", "));
    }

    return { scenario: name, provider, elapsed: result.elapsed, valid: v.valid, errors: v.errors, missions: v.parsed?.missions || [], testType: null, context };
  } catch (err) {
    console.log(`💥 ${err.message}`);
    return { scenario: name, provider, elapsed: 0, valid: false, errors: [err.message], missions: [], testType: null, context };
  }
}

async function runChatbotTest(provider) {
  console.log(`\n${"═".repeat(60)}`);
  console.log(`🧪 [목표 기반] 챗봇 미션추천 (${provider.toUpperCase()})`);
  console.log(`${"═".repeat(60)}`);
  const results = [];
  for (const s of CHATBOT_TEST_SCENARIOS) {
    const r = await runScenario(s.name, buildChatbotPrompt(s.context), provider, s.context);
    r.testType = "chatbot";
    results.push(r);
  }
  return results;
}

async function runInterestTest(provider) {
  console.log(`\n${"═".repeat(60)}`);
  console.log(`🧪 [목표 기반] 관심사 미션추천 (${provider.toUpperCase()})`);
  console.log(`${"═".repeat(60)}`);
  const results = [];
  for (const s of INTEREST_TEST_SCENARIOS) {
    const r = await runScenario(s.name, buildInterestPrompt(s), provider);
    r.testType = "interest";
    results.push(r);
  }
  return results;
}

// ── 결과 저장 ──
function getTimestamp() {
  const now = new Date();
  const pad = (n) => String(n).padStart(2, "0");
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}_${pad(now.getHours())}-${pad(now.getMinutes())}-${pad(now.getSeconds())}`;
}

function formatScenarioResult(r) {
  const status = r.valid ? "PASS" : "FAIL";
  let md = `### ${status} | ${r.scenario} (${r.elapsed}ms)\n\n`;
  if (r.context) {
    const ctx = r.context;
    md += `| 항목 | 내용 |\n|------|------|\n`;
    md += `| 관심사 | ${ctx.category} > ${ctx.subCategory} |\n`;
    md += `| 현재 목표 | ${ctx.goal} |\n`;
    md += `| 최종 결과물 | ${ctx.desiredOutcome} |\n`;
    md += `| 현재 실력 | ${ctx.skillLevel} |\n`;
    md += `| 최근 경험 | ${ctx.recentExperience} |\n`;
    md += `| 목표 기간 | ${ctx.targetPeriod} |\n`;
    md += `| 하루 시간 | ${ctx.dailyAvailableTime} |\n`;
    if (ctx.additionalOpinion) md += `| 추가 의견 | ${ctx.additionalOpinion} |\n`;
    md += `\n`;
  }
  if (!r.valid && r.errors.length) {
    md += `**검증 오류:** ${r.errors.join(", ")}\n\n`;
  }
  if (r.missions.length) {
    md += `| 난이도 | 미션 내용 | 관심사 경로 |\n|--------|----------|------------|\n`;
    for (const m of r.missions.sort((a,b) => a.difficulty - b.difficulty)) {
      const interest = Array.isArray(m.relatedInterest) ? m.relatedInterest.join(" > ") : "-";
      md += `| ${m.difficulty} | ${m.content} | ${interest} |\n`;
    }
    md += "\n";
  }
  return md;
}

function generateMarkdown(provider, testType, results, timestamp) {
  const passed = results.filter(r => r.valid).length;
  const total = results.length;
  const avg = total > 0 ? Math.round(results.reduce((s, r) => s + r.elapsed, 0) / total) : 0;
  const label = testType === "chatbot" ? "챗봇 방식 (새 방식) - 구체적 목표" : "기존 관심사 방식 - 구체적 목표";

  let md = `# ${provider.toUpperCase()} ${label} 미션추천 테스트\n\n`;
  md += `- 실행일시: ${timestamp.replace("_", " ")}\n`;
  md += `- Provider: **${provider.toUpperCase()}**\n`;
  md += `- 추천 방식: **${label}**\n`;
  md += `- 검증 통과: **${passed}/${total}**\n`;
  md += `- 평균 응답시간: **${avg}ms**\n\n---\n\n`;

  for (const r of results) md += formatScenarioResult(r);
  return md;
}

function saveResult(provider, testType, results, timestamp) {
  const dir = path.join(__dirname, "results", provider);
  fs.mkdirSync(dir, { recursive: true });
  const filename = `goals_${testType}_${timestamp}.md`;
  const filepath = path.join(dir, filename);
  fs.writeFileSync(filepath, generateMarkdown(provider, testType, results, timestamp), "utf-8");
  console.log(`📁 결과 저장: results/${provider}/${filename}`);
}

// ── 메인 ──
async function main() {
  const ts = getTimestamp();
  const runAll = args.includes("--all");
  const runChatbotAll = runAll || args.includes("--chatbot");
  const runInterestAll = runAll || args.includes("--interest");
  const allResults = [];

  if (runChatbotAll || args.includes("--chatbot-clova")) {
    const r = await runChatbotTest("clova");
    saveResult("clova", "chatbot", r, ts);
    allResults.push({ provider: "clova", testType: "chatbot", results: r });
  }
  if (runChatbotAll || args.includes("--chatbot-claude")) {
    const r = await runChatbotTest("claude");
    saveResult("claude", "chatbot", r, ts);
    allResults.push({ provider: "claude", testType: "chatbot", results: r });
  }
  if (runInterestAll || args.includes("--interest-clova")) {
    const r = await runInterestTest("clova");
    saveResult("clova", "interest", r, ts);
    allResults.push({ provider: "clova", testType: "interest", results: r });
  }
  if (runInterestAll || args.includes("--interest-claude")) {
    const r = await runInterestTest("claude");
    saveResult("claude", "interest", r, ts);
    allResults.push({ provider: "claude", testType: "interest", results: r });
  }

  console.log(`\n${"═".repeat(60)}`);
  console.log("📊 결과 요약");
  console.log(`${"═".repeat(60)}\n`);
  for (const { provider, testType, results } of allResults) {
    const label = testType === "chatbot" ? "챗봇 방식" : "기존 관심사 방식";
    const passed = results.filter(x => x.valid).length;
    const avg = Math.round(results.reduce((s,x) => s + x.elapsed, 0) / results.length);
    console.log(`[${provider.toUpperCase()} - ${label}] 통과: ${passed}/${results.length} | 평균: ${avg}ms`);
    for (const x of results) console.log(`  ${x.valid ? "✅" : "❌"} ${x.scenario} (${x.elapsed}ms)${!x.valid ? " - " + x.errors[0] : ""}`);
    console.log();
  }
}

main().catch(err => { console.error("💥", err); process.exit(1); });
