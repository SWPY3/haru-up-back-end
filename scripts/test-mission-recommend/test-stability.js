#!/usr/bin/env node

/**
 * 안정성 테스트: 같은 시나리오 5회 반복 → 편차 확인
 *
 * 체크 항목:
 *   [구조] 난이도 하/중/상 각 3개 = 9개 정확히 나오는지
 *   [구조] JSON 파싱 성공률
 *   [구조] info 포함 여부
 *   [내용] 난이도별 미션 난이도가 적절한지 (하 < 중 < 상)
 *   [내용] 사용자 제약사항 반영 여부
 *   [내용] 미션 중복 여부
 */

import fs from "fs";
import path from "path";
import { fileURLToPath } from "url";
import { clovaGenerateText } from "./clova-client.js";
import { claudeGenerateText } from "./claude-client.js";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const REPEAT = 5;

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
{"missions":[{"content":"미션내용","info":"미션정보","difficulty":"하"}]}`;

// 제약사항이 명확한 시나리오 2개로 집중 테스트
const SCENARIOS = [
  {
    name: "10km 마라톤 (무릎 약함)",
    goal: "10km 마라톤 완주하고 싶어요",
    info: "2km만 뛰어도 힘듦, 무릎이 약한 편, 공원 조깅 1.5km에서 포기한 적 있음",
    dailyTime: "40분",
    constraint: "무릎",  // 이 키워드가 info에 반영되는지 체크
  },
  {
    name: "체중 5kg 감량 (운동 초보)",
    goal: "체중 5kg 빼고 싶어요",
    info: "현재 75kg 목표 70kg, 운동 경험 거의 없음, 점심은 구내식당",
    dailyTime: "40분",
    constraint: "초보",
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

function analyzeRun(parsed, constraint) {
  const result = {
    structureOk: false,
    jsonOk: false,
    countCorrect: false,
    levelDistribution: { "하": 0, "중": 0, "상": 0 },
    infoAllPresent: false,
    constraintMentioned: 0,  // info에서 제약사항 키워드가 언급된 횟수
    totalMissions: 0,
    missions: [],
  };

  if (!parsed || !Array.isArray(parsed.missions)) return result;
  result.jsonOk = true;

  const missions = parsed.missions;
  result.totalMissions = missions.length;
  result.missions = missions;

  for (const m of missions) {
    if (result.levelDistribution[m.difficulty] !== undefined) {
      result.levelDistribution[m.difficulty]++;
    }
  }

  result.countCorrect =
    result.levelDistribution["하"] === 3 &&
    result.levelDistribution["중"] === 3 &&
    result.levelDistribution["상"] === 3;

  result.infoAllPresent = missions.every((m) => m.info && m.info.length > 10);

  result.structureOk = result.jsonOk && result.countCorrect && result.infoAllPresent;

  // 제약사항 반영 체크
  if (constraint) {
    for (const m of missions) {
      const text = `${m.content} ${m.info}`.toLowerCase();
      if (text.includes(constraint)) result.constraintMentioned++;
    }
  }

  return result;
}

async function runRepeat(scenario, provider, claudeModel) {
  const label = claudeModel ? `${provider.toUpperCase()} (${claudeModel})` : provider.toUpperCase();
  const generate = provider === "clova" ? clovaGenerateText : claudeGenerateText;

  console.log(`\n${"=".repeat(60)}`);
  console.log(`${scenario.name} | ${label} | ${REPEAT}회 반복`);
  console.log(`${"=".repeat(60)}`);

  const runs = [];

  for (let i = 0; i < REPEAT; i++) {
    console.log(`\n  [${i + 1}/${REPEAT}] 실행 중...`);
    const startTime = Date.now();
    const result = await generate(buildPrompt(scenario), SYSTEM_PROMPT, claudeModel ? { model: claudeModel } : {});
    const elapsed = Date.now() - startTime;

    const parsed = parseResponse(result.content);
    const analysis = analyzeRun(parsed, scenario.constraint);

    runs.push({ round: i + 1, elapsed, ...analysis });

    console.log(`    ${analysis.structureOk ? "O" : "X"} 구조 | 하${analysis.levelDistribution["하"]}/중${analysis.levelDistribution["중"]}/상${analysis.levelDistribution["상"]} | 제약반영 ${analysis.constraintMentioned}건 | ${elapsed}ms`);

    // 미션 내용 간략 출력
    if (analysis.missions.length > 0) {
      for (const level of ["하", "중", "상"]) {
        const levelMissions = analysis.missions.filter((m) => m.difficulty === level);
        console.log(`    [${level}] ${levelMissions.map((m) => m.content).join(" / ")}`);
      }
    }
  }

  return { scenario: scenario.name, provider: label, runs };
}

function generateMarkdown(allResults, timestamp) {
  let md = `안정성 테스트: 동일 시나리오 ${REPEAT}회 반복 결과\n\n`;
  md += `실행일시: ${timestamp.replace("_", " ")}\n`;
  md += `반복 횟수: ${REPEAT}회\n\n---\n\n`;

  for (const { scenario, provider, runs } of allResults) {
    md += `${scenario} | ${provider}\n\n`;

    // 요약 통계
    const structureOkCount = runs.filter((r) => r.structureOk).length;
    const avgElapsed = Math.round(runs.reduce((s, r) => s + r.elapsed, 0) / runs.length);
    const avgConstraint = (runs.reduce((s, r) => s + r.constraintMentioned, 0) / runs.length).toFixed(1);

    md += `구조 성공: ${structureOkCount}/${REPEAT}\n`;
    md += `평균 응답시간: ${avgElapsed}ms\n`;
    md += `평균 제약사항 반영: ${avgConstraint}건/회\n\n`;

    // 미션 중복 분석
    const allContents = runs.flatMap((r) => r.missions.map((m) => m.content));
    const uniqueContents = new Set(allContents);
    md += `총 미션 수: ${allContents.length}개 (고유: ${uniqueContents.size}개, 중복률: ${Math.round((1 - uniqueContents.size / allContents.length) * 100)}%)\n\n`;

    // 난이도별 등장한 미션 목록
    for (const level of ["하", "중", "상"]) {
      const levelContents = runs.flatMap((r) => r.missions.filter((m) => m.difficulty === level).map((m) => m.content));
      const freq = {};
      for (const c of levelContents) freq[c] = (freq[c] || 0) + 1;
      const sorted = Object.entries(freq).sort((a, b) => b[1] - a[1]);

      md += `[난이도 ${level}] ${REPEAT}회에 걸쳐 등장한 미션:\n`;
      for (const [content, count] of sorted) {
        md += `  ${count > 1 ? `(${count}회)` : "     "} ${content}\n`;
      }
      md += `\n`;
    }

    // 각 회차 상세
    for (const run of runs) {
      md += `--- ${run.round}회차 (${run.elapsed}ms) ${run.structureOk ? "PASS" : "FAIL"} ---\n`;
      for (const level of ["하", "중", "상"]) {
        const levelMissions = run.missions.filter((m) => m.difficulty === level);
        for (const m of levelMissions) {
          md += `  [${level}] ${m.content}\n`;
          md += `       ${m.info.substring(0, 100)}${m.info.length > 100 ? "..." : ""}\n`;
        }
      }
      md += `\n`;
    }

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

  for (const scenario of SCENARIOS) {
    for (const { key, label, model } of providers) {
      const result = await runRepeat(scenario, key, model);
      allResults.push(result);
    }
  }

  // 결과 저장
  const dir = path.join(__dirname, "results");
  fs.mkdirSync(dir, { recursive: true });
  const filename = `stability_test_${ts}.md`;
  fs.writeFileSync(path.join(dir, filename), generateMarkdown(allResults, ts), "utf-8");
  console.log(`\n결과 저장: results/${filename}`);

  // 최종 요약
  console.log(`\n${"=".repeat(60)}`);
  console.log("안정성 테스트 최종 요약");
  console.log(`${"=".repeat(60)}\n`);

  for (const { scenario, provider, runs } of allResults) {
    const okCount = runs.filter((r) => r.structureOk).length;
    const avgTime = Math.round(runs.reduce((s, r) => s + r.elapsed, 0) / runs.length);
    const avgConstraint = (runs.reduce((s, r) => s + r.constraintMentioned, 0) / runs.length).toFixed(1);
    const allContents = runs.flatMap((r) => r.missions.map((m) => m.content));
    const unique = new Set(allContents).size;
    const dupRate = Math.round((1 - unique / allContents.length) * 100);

    console.log(`[${provider}] ${scenario}`);
    console.log(`  구조 성공: ${okCount}/${REPEAT} | 평균: ${avgTime}ms | 제약반영: ${avgConstraint}건/회 | 미션 중복률: ${dupRate}%`);
  }
}

main().catch((err) => { console.error(err); process.exit(1); });
