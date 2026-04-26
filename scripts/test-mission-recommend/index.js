#!/usr/bin/env node

/**
 * 미션 추천 AI 비교 테스트 (Clova vs Claude)
 *
 * 결과 파일:
 *   results/{provider}/chatbot_{timestamp}.md   ← 챗봇 방식 (새 방식)
 *   results/{provider}/interest_{timestamp}.md  ← 기존 관심사 방식
 *
 * 사용법:
 *   node index.js --all                # 전체 (챗봇 + 기존, Clova + Claude)
 *   node index.js --chatbot            # 챗봇 방식만 (Clova + Claude)
 *   node index.js --interest           # 기존 방식만 (Clova + Claude)
 *   node index.js --chatbot-clova      # 챗봇 Clova만
 *   node index.js --chatbot-claude     # 챗봇 Claude만
 *   node index.js --interest-clova     # 기존 Clova만
 *   node index.js --interest-claude    # 기존 Claude만
 */

import fs from "fs";
import path from "path";
import { fileURLToPath } from "url";
import { runChatbotTest } from "./test-chatbot-mission.js";
import { runInterestTest } from "./test-interest-mission.js";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const args = process.argv.slice(2);

if (args.length === 0) {
  console.log(`
미션 추천 AI 비교 테스트
========================

사용법:
  node index.js --all                # 전체 (챗봇 + 기존, Clova + Claude)
  node index.js --chatbot            # 챗봇 방식만 (Clova + Claude)
  node index.js --interest           # 기존 방식만 (Clova + Claude)
  node index.js --chatbot-clova      # 챗봇 Clova만
  node index.js --chatbot-claude     # 챗봇 Claude만
  node index.js --interest-clova     # 기존 Clova만
  node index.js --interest-claude    # 기존 Claude만

결과 파일:
  results/{provider}/chatbot_{timestamp}.md   ← 챗봇 방식 (새 방식)
  results/{provider}/interest_{timestamp}.md  ← 기존 관심사 방식
`);
  process.exit(0);
}

function getTimestamp() {
  const now = new Date();
  const pad = (n) => String(n).padStart(2, "0");
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}_${pad(now.getHours())}-${pad(now.getMinutes())}-${pad(now.getSeconds())}`;
}

// ── MD 생성 ──

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

  if (!r.valid && r.errors.length > 0) {
    md += `**검증 오류:**\n`;
    for (const e of r.errors) md += `- ${e}\n`;
    md += `\n`;
  }

  if (r.missions.length > 0) {
    md += `| 난이도 | 미션 내용 | 관심사 경로 |\n|--------|----------|------------|\n`;
    for (const m of r.missions.sort((a, b) => a.difficulty - b.difficulty)) {
      const interest = Array.isArray(m.relatedInterest) ? m.relatedInterest.join(" > ") : "-";
      md += `| ${m.difficulty} | ${m.content} | ${interest} |\n`;
    }
    md += `\n`;
  }

  return md;
}

function generateMarkdown(provider, testType, results, timestamp) {
  const passed = results.filter((r) => r.valid).length;
  const total = results.length;
  const avg = total > 0 ? Math.round(results.reduce((s, r) => s + r.elapsed, 0) / total) : 0;
  const label = testType === "chatbot" ? "챗봇 방식 (새 방식)" : "기존 관심사 방식";

  let md = `# ${provider.toUpperCase()} ${label} 미션추천 테스트 결과\n\n`;
  md += `- 실행일시: ${timestamp.replace("_", " ")}\n`;
  md += `- Provider: **${provider.toUpperCase()}**\n`;
  md += `- 추천 방식: **${label}**\n`;
  md += `- 검증 통과: **${passed}/${total}**\n`;
  md += `- 평균 응답시간: **${avg}ms**\n\n---\n\n`;

  for (const r of results) {
    md += formatScenarioResult(r);
  }

  return md;
}

function saveResult(provider, testType, results, timestamp) {
  const dir = path.join(__dirname, "results", provider);
  fs.mkdirSync(dir, { recursive: true });

  const filename = `${testType}_${timestamp}.md`;
  const filepath = path.join(dir, filename);
  fs.writeFileSync(filepath, generateMarkdown(provider, testType, results, timestamp), "utf-8");
  console.log(`📁 결과 저장: results/${provider}/${filename}`);
  return filepath;
}

// ── 메인 ──

async function main() {
  const timestamp = getTimestamp();
  const runAll = args.includes("--all");
  const runChatbot = runAll || args.includes("--chatbot");
  const runInterest = runAll || args.includes("--interest");

  const allResults = [];

  // 챗봇 방식
  if (runChatbot || args.includes("--chatbot-clova")) {
    const results = await runChatbotTest("clova");
    saveResult("clova", "chatbot", results, timestamp);
    allResults.push({ provider: "clova", testType: "chatbot", results });
  }
  if (runChatbot || args.includes("--chatbot-claude")) {
    const results = await runChatbotTest("claude");
    saveResult("claude", "chatbot", results, timestamp);
    allResults.push({ provider: "claude", testType: "chatbot", results });
  }

  // 기존 관심사 방식
  if (runInterest || args.includes("--interest-clova")) {
    const results = await runInterestTest("clova");
    saveResult("clova", "interest", results, timestamp);
    allResults.push({ provider: "clova", testType: "interest", results });
  }
  if (runInterest || args.includes("--interest-claude")) {
    const results = await runInterestTest("claude");
    saveResult("claude", "interest", results, timestamp);
    allResults.push({ provider: "claude", testType: "interest", results });
  }

  // 콘솔 요약
  printSummary(allResults);
}

function printSummary(allResults) {
  console.log(`\n${"═".repeat(60)}`);
  console.log("📊 테스트 결과 요약");
  console.log(`${"═".repeat(60)}\n`);

  for (const { provider, testType, results } of allResults) {
    const label = testType === "chatbot" ? "챗봇 방식" : "기존 관심사 방식";
    const passed = results.filter((r) => r.valid).length;
    const avg = Math.round(results.reduce((s, r) => s + r.elapsed, 0) / results.length);

    console.log(`[${provider.toUpperCase()} - ${label}] 통과: ${passed}/${results.length} | 평균: ${avg}ms`);
    for (const r of results) {
      const status = r.valid ? "✅" : "❌";
      console.log(`  ${status} ${r.scenario} (${r.elapsed}ms)`);
      if (!r.valid && r.errors.length > 0) {
        r.errors.forEach((e) => console.log(`     └ ${e}`));
      }
    }
    console.log();
  }
}

main().catch((err) => {
  console.error("💥 테스트 실행 중 오류:", err);
  process.exit(1);
});
