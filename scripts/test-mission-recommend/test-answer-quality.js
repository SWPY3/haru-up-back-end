#!/usr/bin/env node

/**
 * 사용자 답변 품질별 미션 추천 비교 테스트
 *
 * 같은 목표에 대해 답변 수준을 3단계로 나눠서 비교:
 *   - 최소: "무릎", "못 뛰어요" (한두 단어)
 *   - 보통: "무릎이 약해요, 2km 정도 뛸 수 있어요" (한 문장)
 *   - 상세: "2km만 뛰어도 힘듦, 무릎이 약한 편, 공원 조깅 1.5km에서 포기" (여러 정보)
 *
 * 미션 품질 차이가 얼마나 나는지 확인
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
7. 사용자 정보가 부족하면 일반적인 수준으로 추천하되, 제공된 정보는 반드시 반영

[난이도 기준]
- 하: 5~15분, 바로 시작할 수 있는 아주 쉬운 활동
- 중: 15~30분, 약간의 집중과 노력이 필요한 활동
- 상: 30~60분, 도전적이지만 하루 안에 끝낼 수 있는 활동

[info 작성 규칙]
- "어떻게 하세요"가 아니라 "이걸 가지고 하세요"에 해당하는 실제 정보
- 사용자가 info만 보고 바로 수행 가능하게

[출력 형식]
{"missions":[{"content":"미션내용","info":"미션정보","difficulty":"하"}]}`;

// 같은 목표, 답변 수준 3단계
const TEST_GROUPS = [
  {
    goal: "10km 마라톤 완주하고 싶어요",
    dailyTime: "40분",
    keyword: "무릎",
    levels: [
      {
        label: "최소 답변",
        answers: "무릎 약함, 못 뛰어요",
      },
      {
        label: "보통 답변",
        answers: "무릎이 약해요. 2km 정도 뛸 수 있는데 힘들어요.",
      },
      {
        label: "상세 답변",
        answers: "2km만 뛰어도 힘듦, 무릎이 약한 편이라 부상 걱정됨. 동네 공원에서 1.5km 조깅하다 포기한 적 있음. 평소 운동 안 함.",
      },
    ],
  },
  {
    goal: "체중 5kg 빼고 싶어요",
    dailyTime: "40분",
    keyword: "구내식당",
    levels: [
      {
        label: "최소 답변",
        answers: "75kg, 운동 안 함",
      },
      {
        label: "보통 답변",
        answers: "현재 75kg이고 70kg까지 빼고 싶어요. 운동 경험 없어요.",
      },
      {
        label: "상세 답변",
        answers: "현재 75kg 목표 70kg. 운동 경험 거의 없고, 점심은 회사 구내식당이라 메뉴 조절이 어려움. 일주일 저녁 안 먹어봤는데 3일 만에 포기.",
      },
    ],
  },
  {
    goal: "토익 900점 달성하고 싶어요",
    dailyTime: "1시간 30분",
    keyword: "LC",
    levels: [
      {
        label: "최소 답변",
        answers: "750점",
      },
      {
        label: "보통 답변",
        answers: "지금 750점이에요. LC가 약해요.",
      },
      {
        label: "상세 답변",
        answers: "현재 750점. LC 파트3,4가 특히 약하고, 모의고사 풀어봤는데 시간 부족했음. RC는 그나마 나은 편.",
      },
    ],
  },
];

function buildPrompt(goal, answers, dailyTime) {
  return `목표: ${goal}
사용자 정보: ${answers}
하루 투자 가능 시간: ${dailyTime}

난이도 하/중/상 각각 3개씩, 총 9개 미션을 추천해주세요.`;
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
  if (!parsed || !Array.isArray(parsed.missions)) return { valid: false, missions: [], keywordCount: 0 };

  const missions = parsed.missions;
  const byDiff = { "하": 0, "중": 0, "상": 0 };
  for (const m of missions) {
    if (byDiff[m.difficulty] !== undefined) byDiff[m.difficulty]++;
  }

  let keywordCount = 0;
  for (const m of missions) {
    const text = `${m.content} ${m.info}`.toLowerCase();
    if (text.includes(keyword.toLowerCase())) keywordCount++;
  }

  const hasInfo = missions.every((m) => m.info && m.info.length > 10);
  const valid = missions.length >= 6 && hasInfo;

  return { valid, missions, byDiff, keywordCount, hasInfo };
}

async function runTest(group, level, provider, claudeModel) {
  const label = claudeModel ? `${provider.toUpperCase()} (${claudeModel.split("-").slice(-2).join("-")})` : provider.toUpperCase();
  const generate = provider === "clova" ? clovaGenerateText : claudeGenerateText;
  const prompt = buildPrompt(group.goal, level.answers, group.dailyTime);

  const startTime = Date.now();
  const result = await generate(prompt, SYSTEM_PROMPT, claudeModel ? { model: claudeModel } : {});
  const elapsed = Date.now() - startTime;

  const parsed = parseResponse(result.content);
  const analysis = analyze(parsed, group.keyword);

  return {
    goal: group.goal,
    answerLevel: level.label,
    answers: level.answers,
    provider: label,
    elapsed,
    ...analysis,
  };
}

function generateMarkdown(allResults, timestamp) {
  let md = `사용자 답변 품질별 미션 추천 비교 테스트\n\n`;
  md += `실행일시: ${timestamp.replace("_", " ")}\n`;
  md += `목적: 같은 목표에 대해 답변이 짧을 때 vs 상세할 때 미션 품질 차이 확인\n\n---\n\n`;

  // 목표별 그룹핑
  const byGoal = {};
  for (const r of allResults) {
    const key = `${r.goal} | ${r.provider}`;
    if (!byGoal[key]) byGoal[key] = [];
    byGoal[key].push(r);
  }

  for (const [key, results] of Object.entries(byGoal)) {
    md += `${key}\n\n`;

    for (const r of results) {
      md += `[${r.answerLevel}] 사용자 답변: "${r.answers}" (${r.elapsed}ms)\n`;
      md += `구조: ${r.valid ? "OK" : "FAIL"} | 키워드 반영: ${r.keywordCount}건 | 미션 수: ${r.missions.length}개\n\n`;

      if (r.missions.length > 0) {
        for (const level of ["하", "중", "상"]) {
          const levelMissions = r.missions.filter((m) => m.difficulty === level);
          if (levelMissions.length === 0) continue;
          for (const m of levelMissions) {
            md += `  [${level}] ${m.content}\n`;
            md += `       ${m.info.substring(0, 120)}${m.info.length > 120 ? "..." : ""}\n`;
          }
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
    { key: "clova", model: null },
    { key: "claude", model: "claude-sonnet-4-6" },
  ];

  const allResults = [];

  for (const group of TEST_GROUPS) {
    for (const { key, model } of providers) {
      const providerLabel = model ? `CLAUDE SONNET` : "CLOVA";
      console.log(`\n${"=".repeat(60)}`);
      console.log(`${group.goal} | ${providerLabel}`);
      console.log(`${"=".repeat(60)}`);

      for (const level of group.levels) {
        console.log(`\n  [${level.label}] "${level.answers}"`);
        const result = await runTest(group, level, key, model);
        allResults.push(result);

        console.log(`    ${result.valid ? "O" : "X"} ${result.missions.length}개 | 키워드 ${result.keywordCount}건 | ${result.elapsed}ms`);
        if (result.missions.length > 0) {
          for (const lv of ["하", "중", "상"]) {
            const lvMissions = result.missions.filter((m) => m.difficulty === lv);
            if (lvMissions.length > 0) {
              console.log(`    [${lv}] ${lvMissions.map((m) => m.content).join(" / ")}`);
            }
          }
        }
      }
    }
  }

  // 저장
  const dir = path.join(__dirname, "results");
  fs.mkdirSync(dir, { recursive: true });
  const filename = `answer_quality_test_${ts}.md`;
  fs.writeFileSync(path.join(dir, filename), generateMarkdown(allResults, ts), "utf-8");
  console.log(`\n결과 저장: results/${filename}`);

  // 요약
  console.log(`\n${"=".repeat(60)}`);
  console.log("답변 품질별 비교 요약");
  console.log(`${"=".repeat(60)}\n`);

  for (const group of TEST_GROUPS) {
    console.log(`[${group.goal}] (키워드: "${group.keyword}")`);
    for (const { key, model } of providers) {
      const label = model ? "SONNET" : "CLOVA";
      const results = allResults.filter(
        (r) => r.goal === group.goal && r.provider.includes(label.substring(0, 4))
      );
      for (const r of results) {
        console.log(`  ${label} | ${r.answerLevel}: 키워드 ${r.keywordCount}건 | ${r.elapsed}ms`);
      }
    }
    console.log();
  }
}

main().catch((err) => { console.error(err); process.exit(1); });
