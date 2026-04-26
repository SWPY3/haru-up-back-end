#!/usr/bin/env node

/**
 * 질문 전략 비교 테스트
 *
 * 방식A: 첫 질문에 후속 질문 3개를 한번에 생성 → 사용자가 한번에 답변 → 미션 추천
 *   LLM 호출 2번 (질문 생성 1번 + 미션 추천 1번)
 *
 * 방식B: 매 답변마다 후속 질문 1개씩 생성 (3라운드)
 *   → 목표 입력 → LLM이 질문1 생성 → 답변 → LLM이 질문2 생성 → 답변 → LLM이 질문3 생성 → 답변 → 미션 추천
 *   LLM 호출 4번 (질문 3번 + 미션 1번)
 *
 * 비교: 미션 품질, 소요 시간, 제약사항 반영도
 */

import fs from "fs";
import path from "path";
import { fileURLToPath } from "url";
import { claudeGenerateText } from "./claude-client.js";

const __dirname = path.dirname(fileURLToPath(import.meta.url));

const MISSION_SYSTEM = `당신은 미션 추천 AI입니다.

[필수 규칙]
1. difficulty는 "상", "중", "하" 3단계만 사용
2. 각 난이도별 3개씩, 총 9개의 미션을 생성
3. content는 10-30자, 한국어
4. info는 미션 수행에 필요한 실제 정보/콘텐츠를 제공
5. JSON만 출력 (마크다운, 설명 금지)
6. URL, 링크 절대 포함 금지
7. 사용자 제약사항을 반드시 반영

[난이도 기준]
- 하: 5~15분, 바로 시작할 수 있는 아주 쉬운 활동
- 중: 15~30분, 약간의 집중과 노력이 필요한 활동
- 상: 30~60분, 도전적이지만 하루 안에 끝낼 수 있는 활동

[출력 형식]
{"missions":[{"content":"미션내용","info":"미션정보","difficulty":"하"}]}`;

// 시나리오: 사용자의 실제 응답을 시뮬레이션
const SCENARIOS = [
  {
    name: "10km 마라톤 (무릎 약함)",
    goal: "10km 마라톤 완주하고 싶어요",
    dailyTime: "40분",
    keyword: "무릎",
    // 방식B에서 각 라운드별 사용자 응답
    roundAnswers: [
      "2km 정도요. 그것도 힘들어요.",           // Q: 현재 달리기 수준?
      "무릎이 약해요. 조깅하면 아파요.",          // Q: 부상/제약사항?
      "동네 공원에서 가끔 걸어요. 운동 습관 없어요.", // Q: 현재 운동 습관?
    ],
    // 방식A에서 한번에 답변
    allAnswers: "현재 2km 정도 뛸 수 있는데 힘들어요. 무릎이 약해서 조깅하면 아파요. 동네 공원에서 가끔 걷는 정도이고 특별한 운동 습관은 없어요.",
  },
  {
    name: "토익 900점",
    goal: "토익 900점 달성하고 싶어요",
    dailyTime: "1시간 30분",
    keyword: "LC",
    roundAnswers: [
      "750점이요.",                          // Q: 현재 점수?
      "LC가 약해요. 특히 파트3,4요.",           // Q: 약한 영역?
      "모의고사 몇 번 풀어봤는데 시간이 부족했어요.",  // Q: 공부 경험?
    ],
    allAnswers: "현재 750점이고, LC가 약해요. 특히 파트3,4가 어려워요. 모의고사 몇 번 풀어봤는데 시간이 부족했어요.",
  },
];

// ── 방식A: 질문 한번에 생성 ──
const QUESTION_BATCH_PROMPT = `사용자의 목표를 구체화하기 위한 후속 질문을 만들어주세요.
좋은 미션을 추천하려면 사용자의 현재 수준, 제약사항, 경험을 알아야 합니다.

[규칙]
1. 질문은 정확히 3개
2. 짧고 명확하게
3. JSON만 출력

[출력 형식]
{"questions":["질문1","질문2","질문3"]}`;

async function runMethodA(scenario) {
  console.log(`\n  [방식A] 질문 한번에 생성`);
  const totalStart = Date.now();

  // Step 1: 질문 3개 한번에 생성
  const q1Start = Date.now();
  const qResult = await claudeGenerateText(
    `사용자 목표: "${scenario.goal}"`,
    QUESTION_BATCH_PROMPT,
    { model: "claude-sonnet-4-6" }
  );
  const q1Elapsed = Date.now() - q1Start;

  let questions = [];
  try {
    const cleaned = qResult.content.replace(/```json?\s*/g, "").replace(/```/g, "").trim();
    const match = cleaned.match(/\{[\s\S]*"questions"[\s\S]*\}/);
    questions = JSON.parse(match ? match[0] : cleaned).questions || [];
  } catch {}

  console.log(`    질문 생성 (${q1Elapsed}ms): ${questions.length}개`);
  questions.forEach((q, i) => console.log(`      Q${i + 1}: ${q}`));

  // Step 2: 미션 추천 (모든 답변을 한번에)
  const m1Start = Date.now();
  const missionPrompt = `목표: ${scenario.goal}
하루 투자 가능 시간: ${scenario.dailyTime}

사용자 추가 정보:
${scenario.allAnswers}

난이도 하/중/상 각각 3개씩, 총 9개 미션을 추천해주세요.`;

  const mResult = await claudeGenerateText(missionPrompt, MISSION_SYSTEM, { model: "claude-sonnet-4-6" });
  const m1Elapsed = Date.now() - m1Start;
  const totalElapsed = Date.now() - totalStart;

  const parsed = parseResponse(mResult.content);
  const analysis = analyze(parsed, scenario.keyword);

  console.log(`    미션 추천 (${m1Elapsed}ms): ${analysis.missions.length}개 | 키워드 ${analysis.keywordCount}건`);
  console.log(`    총 소요: ${totalElapsed}ms (LLM 2회)`);

  return {
    method: "A (한번에)",
    llmCalls: 2,
    questions,
    totalElapsed,
    questionElapsed: q1Elapsed,
    missionElapsed: m1Elapsed,
    ...analysis,
  };
}

// ── 방식B: 매 답변마다 질문 생성 ──
const QUESTION_SINGLE_PROMPT = `사용자의 목표와 지금까지의 대화를 보고, 좋은 미션 추천을 위해 아직 모르는 정보를 파악할 후속 질문 1개를 만들어주세요.

[규칙]
1. 질문은 정확히 1개
2. 이전 대화에서 이미 알게 된 정보는 다시 묻지 마세요
3. JSON만 출력

[출력 형식]
{"question":"질문"}`;

async function runMethodB(scenario) {
  console.log(`\n  [방식B] 매 답변마다 질문 생성`);
  const totalStart = Date.now();
  let questionElapsedTotal = 0;

  const conversation = [];
  const questions = [];

  for (let i = 0; i < 3; i++) {
    // 질문 생성
    const qStart = Date.now();
    const context = conversation.length > 0
      ? `\n지금까지 대화:\n${conversation.map((c) => `Q: ${c.q}\nA: ${c.a}`).join("\n")}`
      : "";

    const qResult = await claudeGenerateText(
      `사용자 목표: "${scenario.goal}"${context}`,
      QUESTION_SINGLE_PROMPT,
      { model: "claude-sonnet-4-6" }
    );
    const qElapsed = Date.now() - qStart;
    questionElapsedTotal += qElapsed;

    let question = "";
    try {
      const cleaned = qResult.content.replace(/```json?\s*/g, "").replace(/```/g, "").trim();
      const match = cleaned.match(/\{[\s\S]*"question"[\s\S]*\}/);
      question = JSON.parse(match ? match[0] : cleaned).question || "";
    } catch {}

    questions.push(question);
    conversation.push({ q: question, a: scenario.roundAnswers[i] });
    console.log(`    Q${i + 1} (${qElapsed}ms): ${question}`);
    console.log(`    A${i + 1}: ${scenario.roundAnswers[i]}`);
  }

  // 미션 추천
  const mStart = Date.now();
  const qaContext = conversation.map((c) => `Q: ${c.q}\nA: ${c.a}`).join("\n");
  const missionPrompt = `목표: ${scenario.goal}
하루 투자 가능 시간: ${scenario.dailyTime}

사용자와의 대화:
${qaContext}

난이도 하/중/상 각각 3개씩, 총 9개 미션을 추천해주세요.`;

  const mResult = await claudeGenerateText(missionPrompt, MISSION_SYSTEM, { model: "claude-sonnet-4-6" });
  const mElapsed = Date.now() - mStart;
  const totalElapsed = Date.now() - totalStart;

  const parsed = parseResponse(mResult.content);
  const analysis = analyze(parsed, scenario.keyword);

  console.log(`    미션 추천 (${mElapsed}ms): ${analysis.missions.length}개 | 키워드 ${analysis.keywordCount}건`);
  console.log(`    총 소요: ${totalElapsed}ms (LLM 4회)`);

  return {
    method: "B (순차)",
    llmCalls: 4,
    questions,
    totalElapsed,
    questionElapsed: questionElapsedTotal,
    missionElapsed: mElapsed,
    ...analysis,
  };
}

function parseResponse(content) {
  try {
    let cleaned = content.replace(/```json?\s*/g, "").replace(/```/g, "").trim();
    const match = cleaned.match(/\{[\s\S]*"missions"\s*:\s*\[[\s\S]*\]\s*\}/);
    if (match) cleaned = match[0];
    return JSON.parse(cleaned);
  } catch {
    return null;
  }
}

function analyze(parsed, keyword) {
  if (!parsed || !Array.isArray(parsed.missions)) return { valid: false, missions: [], keywordCount: 0, byDiff: {} };
  const missions = parsed.missions;
  const byDiff = { "하": 0, "중": 0, "상": 0 };
  for (const m of missions) { if (byDiff[m.difficulty] !== undefined) byDiff[m.difficulty]++; }
  let keywordCount = 0;
  for (const m of missions) {
    if (`${m.content} ${m.info}`.toLowerCase().includes(keyword.toLowerCase())) keywordCount++;
  }
  return { valid: missions.length >= 6, missions, keywordCount, byDiff };
}

function generateMarkdown(allResults, timestamp) {
  let md = `질문 전략 비교 테스트: 한번에 vs 순차\n\n`;
  md += `실행일시: ${timestamp.replace("_", " ")}\n`;
  md += `모델: Claude Sonnet\n\n---\n\n`;

  for (const { scenario, resultA, resultB } of allResults) {
    md += `${scenario}\n\n`;

    for (const r of [resultA, resultB]) {
      md += `[${r.method}] LLM ${r.llmCalls}회 | 총 ${r.totalElapsed}ms (질문 ${r.questionElapsed}ms + 미션 ${r.missionElapsed}ms)\n`;
      md += `미션 ${r.missions.length}개 | 키워드 반영 ${r.keywordCount}건\n\n`;

      md += `생성된 질문:\n`;
      r.questions.forEach((q, i) => { md += `  Q${i + 1}: ${q}\n`; });
      md += `\n`;

      if (r.missions.length > 0) {
        for (const level of ["하", "중", "상"]) {
          const lm = r.missions.filter((m) => m.difficulty === level);
          if (lm.length === 0) continue;
          for (const m of lm) {
            md += `  [${level}] ${m.content}\n`;
            md += `       ${m.info.substring(0, 100)}${m.info.length > 100 ? "..." : ""}\n`;
          }
        }
      }
      md += `\n`;
    }

    // 비교
    md += `비교:\n`;
    md += `  소요시간: 방식A ${resultA.totalElapsed}ms vs 방식B ${resultB.totalElapsed}ms (${resultB.totalElapsed > resultA.totalElapsed ? "B가 " + Math.round((resultB.totalElapsed - resultA.totalElapsed) / 1000) + "초 더 느림" : "A가 더 느림"})\n`;
    md += `  키워드 반영: 방식A ${resultA.keywordCount}건 vs 방식B ${resultB.keywordCount}건\n`;
    md += `  LLM 호출: 방식A ${resultA.llmCalls}회 vs 방식B ${resultB.llmCalls}회\n`;
    md += `\n---\n\n`;
  }

  return md;
}

async function main() {
  const now = new Date();
  const pad = (n) => String(n).padStart(2, "0");
  const ts = `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}_${pad(now.getHours())}-${pad(now.getMinutes())}-${pad(now.getSeconds())}`;

  const allResults = [];

  for (const scenario of SCENARIOS) {
    console.log(`\n${"=".repeat(60)}`);
    console.log(`${scenario.name}`);
    console.log(`${"=".repeat(60)}`);

    const resultA = await runMethodA(scenario);
    const resultB = await runMethodB(scenario);

    allResults.push({ scenario: scenario.name, resultA, resultB });
  }

  // 저장
  const dir = path.join(__dirname, "results");
  fs.mkdirSync(dir, { recursive: true });
  const filename = `question_strategy_test_${ts}.md`;
  fs.writeFileSync(path.join(dir, filename), generateMarkdown(allResults, ts), "utf-8");
  console.log(`\n결과 저장: results/${filename}`);

  // 요약
  console.log(`\n${"=".repeat(60)}`);
  console.log("전략 비교 요약");
  console.log(`${"=".repeat(60)}\n`);

  for (const { scenario, resultA, resultB } of allResults) {
    console.log(`[${scenario}]`);
    console.log(`  방식A (한번에): ${resultA.totalElapsed}ms | LLM ${resultA.llmCalls}회 | 키워드 ${resultA.keywordCount}건 | 미션 ${resultA.missions.length}개`);
    console.log(`  방식B (순차)  : ${resultB.totalElapsed}ms | LLM ${resultB.llmCalls}회 | 키워드 ${resultB.keywordCount}건 | 미션 ${resultB.missions.length}개`);
    console.log();
  }
}

main().catch((err) => { console.error(err); process.exit(1); });
