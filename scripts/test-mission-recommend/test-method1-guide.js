#!/usr/bin/env node

/**
 * 방법 1 + 미션 수행 가이드 테스트
 *
 * 미션 추천 시 각 미션에 수행 방법 가이드를 함께 생성
 * Clova vs Claude 품질 비교
 */

import fs from "fs";
import path from "path";
import { fileURLToPath } from "url";
import { clovaGenerateText } from "./clova-client.js";
import { claudeGenerateText } from "./claude-client.js";

const __dirname = path.dirname(fileURLToPath(import.meta.url));

const SYSTEM_PROMPT = `당신은 미션 추천 AI입니다.

【필수 규칙】
1. difficulty는 1, 2, 3, 4, 5 각각 정확히 1번씩만 사용 (중복 금지)
2. content는 10-30자, 반드시 한국어로만 작성
3. guide는 해당 미션을 수행하는 구체적 방법을 2~3문장으로 작성
4. JSON만 출력 (마크다운, 설명 금지)

【출력 형식】
{"missions":[{"content":"미션내용","guide":"수행 가이드 2~3문장","relatedInterest":["분류1","분류2"],"difficulty":1}]}`;

const SCENARIOS = [
  {
    name: "토익 900점",
    goal: "토익 900점 달성하고 싶어요",
    followUpAnswers: { 현재점수: "지금 750점이에요", 약한부분: "LC 파트3,4가 특히 약해요" },
    dailyTime: "1시간 30분",
    targetPeriod: "3개월",
  },
  {
    name: "체중 5kg 감량",
    goal: "체중 5kg 빼고 싶어요",
    followUpAnswers: { 현재상태: "현재 75kg, 목표 70kg", 제약: "점심은 구내식당이라 조절 어려움" },
    dailyTime: "40분",
    targetPeriod: "3개월",
  },
  {
    name: "기타로 노래 반주",
    goal: "기타 쳐서 노래 반주하고 싶어요",
    followUpAnswers: { 수준: "완전 초보, 기타를 막 샀어요", 경험: "유튜브로 코드 잡는 영상 하나 봤어요" },
    dailyTime: "30분",
    targetPeriod: "4개월",
  },
  {
    name: "일본어 JLPT N3",
    goal: "JLPT N3 합격하고 싶어요",
    followUpAnswers: { 수준: "히라가나/가타카나만 읽을 수 있음", 어려운점: "한자가 너무 어려워요" },
    dailyTime: "40분",
    targetPeriod: "8개월",
  },
  {
    name: "마라톤 10km 완주",
    goal: "10km 마라톤 완주하고 싶어요",
    followUpAnswers: { 체력: "2km만 뛰어도 힘듦", 제약: "무릎이 약한 편이에요" },
    dailyTime: "40분",
    targetPeriod: "5개월",
  },
  {
    name: "매일 명상 습관",
    goal: "매일 명상해서 스트레스 관리하고 싶어요",
    followUpAnswers: { 상태: "대학원생, 논문 스트레스 심함", 경험: "심호흡 5분 해봤는데 잡생각 많았어요" },
    dailyTime: "15분",
    targetPeriod: "2개월",
  },
];

function parseMinutes(input) {
  const m1 = input.match(/(\d+)\s*시간(?:\s*(\d+)\s*분)?/);
  if (m1) return parseInt(m1[1]) * 60 + (parseInt(m1[2]) || 0);
  const m2 = input.match(/(\d+)\s*분/);
  if (m2) return parseInt(m2[1]);
  return null;
}

function buildPrompt(scenario) {
  const minutes = parseMinutes(scenario.dailyTime);
  const qaContext = Object.entries(scenario.followUpAnswers)
    .map(([k, v]) => `- ${k}: ${v}`)
    .join("\n");

  const jsonEx = [1, 2, 3, 4, 5]
    .map(
      (d) =>
        `{"content": "미션내용", "guide": "이 미션을 수행하는 구체적 방법 2~3문장", "relatedInterest": ["분류1", "분류2"], "difficulty": ${d}}`
    )
    .join(",\n    ");

  let prompt = `목표: ${scenario.goal}
하루 투자 가능 시간: ${scenario.dailyTime}
목표 기간: ${scenario.targetPeriod}

===== 사용자 정보 =====
${qaContext}

위 정보를 기반으로 난이도 1~5 각각 1개씩, 총 5개 미션을 추천하세요.
각 미션에는 **guide** 필드로 수행 방법을 구체적으로 안내해주세요.

guide 작성 규칙:
- 2~3문장으로 작성
- "어떻게 시작하고, 무엇을 하고, 어떻게 마무리하는지" 순서로
- 초보자도 바로 따라할 수 있게 구체적으로
- 필요한 도구/앱/자료가 있으면 추천
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

function parseResponse(content) {
  try {
    let cleaned = content.replace(/```json?\s*/g, "").replace(/```/g, "").trim();
    const match = cleaned.match(/\{[\s\S]*"missions"\s*:\s*\[[\s\S]*\]\s*\}/);
    if (match) cleaned = match[0];
    try {
      return JSON.parse(cleaned);
    } catch {
      const blocks = [
        ...cleaned.matchAll(
          /"content"\s*:\s*"([^"]*)"\s*,\s*"guide"\s*:\s*"([^"]*)"\s*,\s*"relatedInterest"\s*:\s*(\[[^\]]*\])\s*,\s*"difficulty"\s*:\s*(\d)/g
        ),
      ];
      if (blocks.length > 0) {
        return {
          missions: blocks.map((m) => ({
            content: m[1],
            guide: m[2],
            relatedInterest: JSON.parse(m[3]),
            difficulty: parseInt(m[4]),
          })),
        };
      }
      throw new Error("파싱 실패");
    }
  } catch (e) {
    return null;
  }
}

async function runScenario(scenario, provider) {
  const generate = provider === "clova" ? clovaGenerateText : claudeGenerateText;
  const prompt = buildPrompt(scenario);

  console.log(`\n${"─".repeat(60)}`);
  console.log(`📋 ${scenario.name} | ${provider.toUpperCase()}`);
  console.log(`${"─".repeat(60)}`);

  const startTime = Date.now();
  const result = await generate(prompt, SYSTEM_PROMPT);
  const elapsed = Date.now() - startTime;

  const parsed = parseResponse(result.content);
  const missions = parsed?.missions || [];
  const valid = missions.length === 5 && new Set(missions.map((m) => m.difficulty)).size === 5;
  const hasGuide = missions.every((m) => m.guide && m.guide.length > 10);

  if (valid) {
    console.log(`✅ 검증 통과 | 가이드 ${hasGuide ? "✅" : "❌"} (${elapsed}ms)`);
    for (const m of missions.sort((a, b) => a.difficulty - b.difficulty)) {
      console.log(`  난이도${m.difficulty}: ${m.content}`);
      console.log(`    📖 ${m.guide || "(가이드 없음)"}`);
    }
  } else {
    console.log(`❌ 검증 실패 (${elapsed}ms)`);
  }

  return {
    scenario: scenario.name,
    provider,
    goal: scenario.goal,
    dailyTime: scenario.dailyTime,
    targetPeriod: scenario.targetPeriod,
    followUpAnswers: scenario.followUpAnswers,
    missions,
    valid,
    hasGuide,
    elapsed,
  };
}

function generateMarkdown(provider, results, timestamp) {
  const passed = results.filter((r) => r.valid).length;
  const guideOk = results.filter((r) => r.hasGuide).length;
  const avg = Math.round(results.reduce((s, r) => s + r.elapsed, 0) / results.length);

  let md = `# ${provider.toUpperCase()} 미션추천 + 수행 가이드 테스트\n\n`;
  md += `- 실행일시: ${timestamp.replace("_", " ")}\n`;
  md += `- Provider: **${provider.toUpperCase()}**\n`;
  md += `- 검증 통과: **${passed}/${results.length}**\n`;
  md += `- 가이드 포함: **${guideOk}/${results.length}**\n`;
  md += `- 평균 응답시간: **${avg}ms**\n\n---\n\n`;

  for (const r of results) {
    md += `## ${r.valid ? "PASS" : "FAIL"} | ${r.scenario} (${r.elapsed}ms)\n\n`;

    md += `| 항목 | 내용 |\n|------|------|\n`;
    md += `| 목표 | ${r.goal} |\n`;
    md += `| 하루 시간 | ${r.dailyTime} |\n`;
    md += `| 목표 기간 | ${r.targetPeriod} |\n`;
    for (const [k, v] of Object.entries(r.followUpAnswers)) {
      md += `| ${k} | ${v} |\n`;
    }
    md += `\n`;

    if (r.missions.length > 0) {
      md += `| 난이도 | 미션 내용 | 수행 가이드 |\n|--------|----------|------------|\n`;
      for (const m of r.missions.sort((a, b) => a.difficulty - b.difficulty)) {
        md += `| ${m.difficulty} | ${m.content} | ${m.guide || "-"} |\n`;
      }
      md += `\n`;
    }
  }

  return md;
}

async function main() {
  const now = new Date();
  const pad = (n) => String(n).padStart(2, "0");
  const ts = `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}_${pad(now.getHours())}-${pad(now.getMinutes())}-${pad(now.getSeconds())}`;

  const allResults = [];

  for (const provider of ["clova", "claude"]) {
    console.log(`\n${"═".repeat(60)}`);
    console.log(`🧪 미션 + 수행 가이드 테스트 (${provider.toUpperCase()})`);
    console.log(`${"═".repeat(60)}`);

    const results = [];
    for (const scenario of SCENARIOS) {
      results.push(await runScenario(scenario, provider));
    }

    const dir = path.join(__dirname, "results", provider);
    fs.mkdirSync(dir, { recursive: true });
    const filename = `method1_guide_${ts}.md`;
    fs.writeFileSync(path.join(dir, filename), generateMarkdown(provider, results, ts), "utf-8");
    console.log(`\n📁 결과 저장: results/${provider}/${filename}`);

    allResults.push({ provider, results });
  }

  // 요약
  console.log(`\n${"═".repeat(60)}`);
  console.log("📊 결과 요약");
  console.log(`${"═".repeat(60)}\n`);

  for (const { provider, results } of allResults) {
    const passed = results.filter((r) => r.valid).length;
    const guideOk = results.filter((r) => r.hasGuide).length;
    const avg = Math.round(results.reduce((s, r) => s + r.elapsed, 0) / results.length);
    console.log(
      `[${provider.toUpperCase()}] 통과: ${passed}/${results.length} | 가이드: ${guideOk}/${results.length} | 평균: ${avg}ms`
    );
    for (const r of results) {
      console.log(
        `  ${r.valid ? "✅" : "❌"} ${r.scenario} (${r.elapsed}ms) ${r.hasGuide ? "📖가이드OK" : "⚠️가이드부족"}`
      );
    }
    console.log();
  }
}

main().catch((err) => {
  console.error("💥", err);
  process.exit(1);
});
