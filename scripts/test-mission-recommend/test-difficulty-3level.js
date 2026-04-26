#!/usr/bin/env node

/**
 * 난이도 상/중/하 + 난이도별 2~3개 미션 생성 테스트
 * 체력관리 분야 집중 테스트
 *
 * 기존: difficulty 1~5, 각 1개 = 총 5개
 * 변경: difficulty 상/중/하, 각 2~3개 = 총 6~9개
 */

import fs from "fs";
import path from "path";
import { fileURLToPath } from "url";
import { clovaGenerateText } from "./clova-client.js";
import { claudeGenerateText } from "./claude-client.js";

const __dirname = path.dirname(fileURLToPath(import.meta.url));

const SYSTEM_PROMPT = `당신은 미션 추천 AI입니다.

[필수 규칙]
1. difficulty는 "상", "중", "하" 3단계만 사용
2. 각 난이도별 3개씩, 총 9개의 미션을 생성
3. content는 10-30자, 한국어
4. info는 미션 수행에 필요한 실제 정보/콘텐츠를 제공
5. JSON만 출력 (마크다운, 설명 금지)
6. URL, 링크 절대 포함 금지

[난이도 기준]
- 하: 5~15분, 바로 시작할 수 있는 아주 쉬운 활동
- 중: 15~30분, 약간의 집중과 노력이 필요한 활동
- 상: 30~60분, 도전적이지만 하루 안에 끝낼 수 있는 활동

[info 작성 규칙]
- "어떻게 하세요"가 아니라 "이걸 가지고 하세요"에 해당하는 실제 정보
- 운동이면 세트/횟수/자세/호흡법/주의사항 등 구체적으로
- 사용자가 info만 보고 바로 수행 가능하게

[출력 형식]
{"missions":[{"content":"미션내용","info":"미션정보","difficulty":"하"},{"content":"미션내용","info":"미션정보","difficulty":"하"},{"content":"미션내용","info":"미션정보","difficulty":"하"},{"content":"미션내용","info":"미션정보","difficulty":"중"},{"content":"미션내용","info":"미션정보","difficulty":"중"},{"content":"미션내용","info":"미션정보","difficulty":"중"},{"content":"미션내용","info":"미션정보","difficulty":"상"},{"content":"미션내용","info":"미션정보","difficulty":"상"},{"content":"미션내용","info":"미션정보","difficulty":"상"}]}`;

const SCENARIOS = [
  {
    name: "체중 5kg 감량 (운동 초보)",
    goal: "체중 5kg 빼고 싶어요",
    info: "현재 75kg 목표 70kg, 운동 경험 거의 없음, 점심은 구내식당",
    dailyTime: "40분",
  },
  {
    name: "10km 마라톤 완주 (무릎 약함)",
    goal: "10km 마라톤 완주하고 싶어요",
    info: "2km만 뛰어도 힘듦, 무릎이 약한 편, 공원 조깅 1.5km에서 포기한 적 있음",
    dailyTime: "40분",
  },
  {
    name: "벤치프레스 80kg 달성",
    goal: "벤치프레스 80kg 치고 싶어요",
    info: "현재 50kg 3회 가능, 헬스장 6개월 다니는 중, 어깨가 좀 불편함",
    dailyTime: "1시간",
  },
  {
    name: "유연성 향상 (허리통증)",
    goal: "몸이 너무 뻣뻣해서 유연성 키우고 싶어요",
    info: "앉아서 발끝 못 닿음, 사무직이라 하루종일 앉아있음, 허리통증 있음",
    dailyTime: "30분",
  },
  {
    name: "수영 자유형 500m 완영",
    goal: "자유형으로 500m 쉬지 않고 수영하고 싶어요",
    info: "25m 겨우 가능, 숨쉬기가 어려움, 주 2회 수영장 다니는 중",
    dailyTime: "1시간",
  },
];

function buildPrompt(scenario) {
  return `목표: ${scenario.goal}
사용자 정보: ${scenario.info}
하루 투자 가능 시간: ${scenario.dailyTime}

난이도 하/중/상 각각 3개씩, 총 9개 미션을 추천해주세요.
각 미션의 info에는 수행에 필요한 실제 정보를 담아주세요.`;
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

function validateResult(parsed) {
  if (!parsed || !Array.isArray(parsed.missions)) return { valid: false, errors: ["missions 배열 없음"] };

  const missions = parsed.missions;
  const errors = [];

  const byDiff = { "하": [], "중": [], "상": [] };
  for (const m of missions) {
    if (byDiff[m.difficulty] !== undefined) {
      byDiff[m.difficulty].push(m);
    } else {
      errors.push(`알 수 없는 난이도: "${m.difficulty}"`);
    }
  }

  for (const [level, list] of Object.entries(byDiff)) {
    if (list.length < 2) errors.push(`난이도 ${level}: ${list.length}개 (최소 2개 필요)`);
    if (list.length > 3) errors.push(`난이도 ${level}: ${list.length}개 (최대 3개 초과)`);
  }

  for (const m of missions) {
    if (!m.content) errors.push(`content 누락`);
    if (!m.info || m.info.length < 10) errors.push(`info 부족: "${m.content}"`);
  }

  return { valid: errors.length === 0, errors };
}

async function runScenario(scenario, provider, claudeModel) {
  const label = claudeModel ? `${provider.toUpperCase()} (${claudeModel})` : provider.toUpperCase();
  const generate = provider === "clova" ? clovaGenerateText : claudeGenerateText;
  const prompt = buildPrompt(scenario);

  console.log(`\n${"─".repeat(60)}`);
  console.log(`${scenario.name} | ${label}`);
  console.log(`${"─".repeat(60)}`);

  const startTime = Date.now();
  const result = await generate(prompt, SYSTEM_PROMPT, claudeModel ? { model: claudeModel } : {});
  const elapsed = Date.now() - startTime;

  const parsed = parseResponse(result.content);
  const { valid, errors } = validateResult(parsed);
  const missions = parsed?.missions || [];

  if (valid) {
    console.log(`통과 (${elapsed}ms) - ${missions.length}개 미션`);
    for (const level of ["하", "중", "상"]) {
      const levelMissions = missions.filter((m) => m.difficulty === level);
      console.log(`  [${level}] ${levelMissions.length}개:`);
      for (const m of levelMissions) {
        console.log(`    - ${m.content}`);
        console.log(`      ${m.info.substring(0, 80)}${m.info.length > 80 ? "..." : ""}`);
      }
    }
  } else {
    console.log(`실패 (${elapsed}ms): ${errors.join(", ")}`);
    if (missions.length > 0) {
      for (const m of missions) {
        console.log(`  [${m.difficulty}] ${m.content}`);
      }
    }
  }

  return { scenario: scenario.name, provider: label, missions, valid, errors, elapsed };
}

function generateMarkdown(label, results, timestamp) {
  const passed = results.filter((r) => r.valid).length;
  const avg = Math.round(results.reduce((s, r) => s + r.elapsed, 0) / results.length);

  let md = `${label} 난이도 상/중/하 미션추천 테스트 (체력관리 집중)\n\n`;
  md += `실행일시: ${timestamp.replace("_", " ")}\n`;
  md += `통과: ${passed}/${results.length}\n`;
  md += `평균 응답시간: ${avg}ms\n\n---\n\n`;

  for (const r of results) {
    md += `${r.valid ? "PASS" : "FAIL"} | ${r.scenario} (${r.elapsed}ms)\n\n`;

    if (r.missions.length > 0) {
      for (const level of ["하", "중", "상"]) {
        const levelMissions = r.missions.filter((m) => m.difficulty === level);
        if (levelMissions.length === 0) continue;
        md += `[난이도 ${level}] ${levelMissions.length}개\n`;
        for (const m of levelMissions) {
          md += `- ${m.content}\n`;
          md += `  info: ${m.info}\n`;
        }
        md += `\n`;
      }
    }

    if (!r.valid) md += `오류: ${r.errors.join(", ")}\n\n`;
    md += `---\n\n`;
  }

  return md;
}

async function main() {
  const now = new Date();
  const pad = (n) => String(n).padStart(2, "0");
  const ts = `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}_${pad(now.getHours())}-${pad(now.getMinutes())}-${pad(now.getSeconds())}`;

  const providers = [
    { key: "clova", label: "CLOVA", model: null },
    { key: "claude", label: "CLAUDE SONNET", model: "claude-sonnet-4-6" },
  ];

  const allResults = [];

  for (const { key, label, model } of providers) {
    console.log(`\n${"=".repeat(60)}`);
    console.log(`난이도 상/중/하 테스트 (${label})`);
    console.log(`${"=".repeat(60)}`);

    const results = [];
    for (const scenario of SCENARIOS) {
      results.push(await runScenario(scenario, key, model));
    }

    const dirName = model ? `${key}_${model.includes("sonnet") ? "sonnet" : "opus"}` : key;
    const dir = path.join(__dirname, "results", dirName);
    fs.mkdirSync(dir, { recursive: true });
    const filename = `difficulty_3level_${ts}.md`;
    fs.writeFileSync(path.join(dir, filename), generateMarkdown(label, results, ts), "utf-8");
    console.log(`\n결과 저장: results/${dirName}/${filename}`);
    allResults.push({ label, results });
  }

  console.log(`\n${"=".repeat(60)}`);
  console.log("결과 요약");
  console.log(`${"=".repeat(60)}\n`);
  for (const { label, results } of allResults) {
    const passed = results.filter((r) => r.valid).length;
    const avg = Math.round(results.reduce((s, r) => s + r.elapsed, 0) / results.length);
    console.log(`[${label}] 통과: ${passed}/${results.length} | 평균: ${avg}ms`);
    for (const r of results) {
      const mCount = r.missions.length;
      const byLevel = { "하": 0, "중": 0, "상": 0 };
      r.missions.forEach((m) => { if (byLevel[m.difficulty] !== undefined) byLevel[m.difficulty]++; });
      console.log(`  ${r.valid ? "O" : "X"} ${r.scenario} (${r.elapsed}ms) - 하${byLevel["하"]}/중${byLevel["중"]}/상${byLevel["상"]} = ${mCount}개`);
    }
    console.log();
  }
}

main().catch((err) => { console.error(err); process.exit(1); });
