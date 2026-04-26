#!/usr/bin/env node

/**
 * 미션 추천 + 수행 가이드 + 참고 링크 테스트
 *
 * 2단계:
 *   [Step1] Claude: 미션 5개 + 가이드 생성 (도구 없음)
 *   [Step2] Claude + WebSearch: 각 미션에 맞는 참고 링크 검색
 */

import fs from "fs";
import path from "path";
import { fileURLToPath } from "url";
import { query } from "@anthropic-ai/claude-agent-sdk";
import { SYSTEM_PROMPT, validateMissionResponse } from "./config.js";

const __dirname = path.dirname(fileURLToPath(import.meta.url));

const SCENARIOS = [
  {
    name: "토익 900점",
    goal: "토익 900점 달성하고 싶어요",
    info: "현재 750점, LC 파트3,4가 약함",
    dailyTime: "1시간 30분",
    targetPeriod: "3개월",
  },
  {
    name: "체중 5kg 감량",
    goal: "체중 5kg 빼고 싶어요",
    info: "현재 75kg 목표 70kg, 운동 경험 없음, 점심은 구내식당",
    dailyTime: "40분",
    targetPeriod: "3개월",
  },
  {
    name: "기타로 노래 반주",
    goal: "기타 쳐서 노래 반주하고 싶어요",
    info: "완전 초보, 기타를 막 샀음",
    dailyTime: "30분",
    targetPeriod: "4개월",
  },
];

// ── Step 1: 미션 + 가이드 생성 (WebSearch 없이) ──
const GUIDE_SYSTEM = `당신은 미션 추천 AI입니다.

【필수 규칙】
1. difficulty는 1, 2, 3, 4, 5 각각 정확히 1번씩만 사용 (중복 금지)
2. content는 10-30자, 반드시 한국어
3. guide는 수행 방법 2~3문장
4. JSON만 출력

【출력 형식】
{"missions":[{"content":"미션","guide":"가이드 2~3문장","difficulty":1}]}`;

function buildStep1Prompt(scenario) {
  return `목표: ${scenario.goal}
사용자 정보: ${scenario.info}
하루 시간: ${scenario.dailyTime} / 목표 기간: ${scenario.targetPeriod}

난이도 1~5 각각 1개씩 미션을 추천하고, 각 미션에 수행 가이드를 붙여주세요.`;
}

// ── Step 2: WebSearch로 링크 검색 ──
function buildStep2Prompt(missions) {
  const missionList = missions
    .sort((a, b) => a.difficulty - b.difficulty)
    .map((m) => `- 난이도${m.difficulty}: ${m.content}`)
    .join("\n");

  return `아래 미션들에 대해 각각 실제 도움이 될 유튜브 영상이나 블로그 글을 WebSearch로 검색해서 찾아주세요.

${missionList}

각 미션당 1~2개 링크를 찾고, 아래 JSON 형식으로만 출력해주세요:
{"links":{"1":[{"title":"제목","url":"URL"}],"2":[{"title":"제목","url":"URL"}],"3":[],"4":[],"5":[]}}

규칙:
- 반드시 WebSearch로 검색한 실제 URL만 포함
- 검색 결과가 없으면 빈 배열 []
- JSON만 출력`;
}

function parseMissions(text) {
  try {
    let cleaned = text.replace(/```json?\s*/g, "").replace(/```/g, "").trim();
    const match = cleaned.match(/\{[\s\S]*"missions"\s*:\s*\[[\s\S]*\]\s*\}/);
    if (match) cleaned = match[0];
    return JSON.parse(cleaned).missions || [];
  } catch {
    return [];
  }
}

function parseLinks(text) {
  try {
    let cleaned = text.replace(/```json?\s*/g, "").replace(/```/g, "").trim();
    const match = cleaned.match(/\{[\s\S]*"links"\s*:\s*\{[\s\S]*\}\s*\}/);
    if (match) cleaned = match[0];
    return JSON.parse(cleaned).links || {};
  } catch {
    return {};
  }
}

async function claudeQuery(prompt, systemPrompt, tools, maxTurns) {
  let resultText = "";
  for await (const message of query({
    prompt,
    options: {
      model: "claude-opus-4-6",
      maxTurns: maxTurns || 1,
      allowedTools: tools || [],
      systemPrompt: systemPrompt || undefined,
      permissionMode: "bypassPermissions",
      allowDangerouslySkipPermissions: true,
    },
  })) {
    if ("result" in message) {
      resultText = message.result;
    }
  }
  return resultText;
}

async function runScenario(scenario) {
  console.log(`\n${"─".repeat(60)}`);
  console.log(`📋 ${scenario.name}`);
  console.log(`${"─".repeat(60)}`);

  // Step 1: 미션 + 가이드
  console.log("[Step1] 미션 + 가이드 생성...");
  const step1Start = Date.now();
  const step1Result = await claudeQuery(buildStep1Prompt(scenario), GUIDE_SYSTEM, [], 1);
  const step1Elapsed = Date.now() - step1Start;

  const missions = parseMissions(step1Result);
  const valid = missions.length === 5 && new Set(missions.map((m) => m.difficulty)).size === 5;

  if (!valid) {
    console.log(`❌ Step1 실패 (${step1Elapsed}ms)`);
    return { scenario: scenario.name, missions: [], links: {}, valid: false, step1Elapsed, step2Elapsed: 0, totalElapsed: step1Elapsed, ...scenario };
  }

  console.log(`✅ Step1 완료 (${step1Elapsed}ms)`);
  for (const m of missions.sort((a, b) => a.difficulty - b.difficulty)) {
    console.log(`  난이도${m.difficulty}: ${m.content}`);
    console.log(`    📖 ${m.guide}`);
  }

  // Step 2: WebSearch로 링크 검색
  console.log("\n[Step2] 참고 링크 검색...");
  const step2Start = Date.now();
  const step2Result = await claudeQuery(buildStep2Prompt(missions), null, ["WebSearch"], 10);
  const step2Elapsed = Date.now() - step2Start;

  const links = parseLinks(step2Result);
  const linkCount = Object.values(links).reduce((sum, arr) => sum + (Array.isArray(arr) ? arr.length : 0), 0);

  console.log(`✅ Step2 완료 (${step2Elapsed}ms) - 링크 ${linkCount}개`);

  // 링크 합치기
  for (const m of missions) {
    m.links = links[String(m.difficulty)] || [];
  }

  for (const m of missions.sort((a, b) => a.difficulty - b.difficulty)) {
    if (m.links.length > 0) {
      for (const link of m.links) {
        console.log(`  🔗 난이도${m.difficulty}: ${link.title} - ${link.url}`);
      }
    }
  }

  const totalElapsed = step1Elapsed + step2Elapsed;
  console.log(`⏱  총 소요: ${totalElapsed}ms (미션 ${step1Elapsed}ms + 검색 ${step2Elapsed}ms)`);

  return {
    scenario: scenario.name,
    goal: scenario.goal,
    info: scenario.info,
    dailyTime: scenario.dailyTime,
    targetPeriod: scenario.targetPeriod,
    missions,
    valid,
    linkCount,
    step1Elapsed,
    step2Elapsed,
    totalElapsed,
  };
}

function generateMarkdown(results, timestamp) {
  const passed = results.filter((r) => r.valid).length;
  const totalLinks = results.reduce((s, r) => s + r.linkCount, 0);
  const avg = Math.round(results.reduce((s, r) => s + r.totalElapsed, 0) / results.length);

  let md = `# CLAUDE 미션추천 + 가이드 + 참고링크 테스트\n\n`;
  md += `- 실행일시: ${timestamp.replace("_", " ")}\n`;
  md += `- Provider: **CLAUDE (Agent SDK + WebSearch)**\n`;
  md += `- 검증 통과: **${passed}/${results.length}**\n`;
  md += `- 총 참고 링크: **${totalLinks}개**\n`;
  md += `- 평균 응답시간: **${avg}ms**\n\n---\n\n`;

  for (const r of results) {
    md += `## ${r.valid ? "PASS" : "FAIL"} | ${r.scenario} (${r.totalElapsed}ms)\n\n`;
    md += `| 항목 | 내용 |\n|------|------|\n`;
    md += `| 목표 | ${r.goal} |\n`;
    md += `| 사용자 정보 | ${r.info} |\n`;
    md += `| 하루 시간 | ${r.dailyTime} |\n`;
    md += `| 목표 기간 | ${r.targetPeriod} |\n\n`;

    for (const m of r.missions.sort((a, b) => a.difficulty - b.difficulty)) {
      md += `### 난이도 ${m.difficulty}: ${m.content}\n\n`;
      md += `**가이드:** ${m.guide || "-"}\n\n`;
      if (m.links && m.links.length > 0) {
        md += `**참고 자료:**\n`;
        for (const link of m.links) {
          md += `- [${link.title}](${link.url})\n`;
        }
        md += `\n`;
      }
    }
  }
  return md;
}

async function main() {
  const now = new Date();
  const pad = (n) => String(n).padStart(2, "0");
  const ts = `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}_${pad(now.getHours())}-${pad(now.getMinutes())}-${pad(now.getSeconds())}`;

  console.log(`${"═".repeat(60)}`);
  console.log(`🧪 미션 + 가이드 + 참고링크 테스트 (2단계)`);
  console.log(`${"═".repeat(60)}`);

  const results = [];
  for (const scenario of SCENARIOS) {
    results.push(await runScenario(scenario));
  }

  const dir = path.join(__dirname, "results", "claude");
  fs.mkdirSync(dir, { recursive: true });
  const filename = `method1_guide_links_${ts}.md`;
  fs.writeFileSync(path.join(dir, filename), generateMarkdown(results, ts), "utf-8");
  console.log(`\n📁 결과 저장: results/claude/${filename}`);

  console.log(`\n${"═".repeat(60)}`);
  console.log("📊 결과 요약");
  console.log(`${"═".repeat(60)}\n`);
  const passed = results.filter((r) => r.valid).length;
  const totalLinks = results.reduce((s, r) => s + r.linkCount, 0);
  const avg = Math.round(results.reduce((s, r) => s + r.totalElapsed, 0) / results.length);
  console.log(`통과: ${passed}/${results.length} | 참고링크: ${totalLinks}개 | 평균: ${avg}ms`);
  for (const r of results) {
    console.log(`  ${r.valid ? "✅" : "❌"} ${r.scenario} (${r.totalElapsed}ms) 🔗${r.linkCount}개`);
  }
}

main().catch((err) => { console.error("💥", err); process.exit(1); });
