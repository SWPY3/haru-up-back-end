#!/usr/bin/env node

/**
 * 미션 추천 + 미션 정보(info) 테스트
 *
 * guide(수행 방법)가 아닌 info(미션 수행에 필요한 실제 콘텐츠)를 함께 제공
 * 예: "가타카나 1개 암기" → info에 외워야 할 가타카나를 알려줌
 *     "토익 단어 10개 암기" → info에 실제 단어 10개를 제공
 *     "스쿼트 3세트" → info에 올바른 자세와 호흡법을 설명
 *
 * Clova vs Claude 비교
 */

import fs from "fs";
import path from "path";
import { fileURLToPath } from "url";
import { SYSTEM_PROMPT, validateMissionResponse } from "./config.js";
import { clovaGenerateText } from "./clova-client.js";
import { claudeGenerateText } from "./claude-client.js";

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
  {
    name: "일본어 JLPT N3",
    goal: "JLPT N3 합격하고 싶어요",
    info: "히라가나/가타카나만 읽을 수 있음, 한자가 어려움",
    dailyTime: "40분",
    targetPeriod: "8개월",
  },
  {
    name: "마라톤 10km 완주",
    goal: "10km 마라톤 완주하고 싶어요",
    info: "2km만 뛰어도 힘듦, 무릎이 약한 편",
    dailyTime: "40분",
    targetPeriod: "5개월",
  },
  {
    name: "매일 명상 습관",
    goal: "매일 명상해서 스트레스 관리하고 싶어요",
    info: "대학원생, 논문 스트레스 심함, 심호흡 5분 해봤는데 잡생각 많음",
    dailyTime: "15분",
    targetPeriod: "2개월",
  },
];

const INFO_SYSTEM = `당신은 미션 추천 AI입니다.

[필수 규칙]
1. difficulty는 1, 2, 3, 4, 5 각각 정확히 1번씩만 사용 (중복 금지)
2. content는 10-30자, 한국어
3. info는 해당 미션을 수행하는 데 필요한 실제 콘텐츠/정보를 제공
4. JSON만 출력 (마크다운, 설명 금지)

[info 작성 규칙 - 매우 중요]
- info는 "어떻게 하세요"가 아니라 "이걸 가지고 하세요"에 해당하는 정보
- 사용자가 info만 보고 바로 미션을 수행할 수 있도록 실제 데이터를 제공
- 예시:
  * "토익 단어 10개 암기" → info에 실제 단어 10개와 뜻을 나열
  * "가타카나 5개 암기" → info에 가타카나 5개와 발음/뜻을 제공
  * "스쿼트 3세트 15회" → info에 올바른 자세, 호흡법, 주의사항 설명
  * "영어 일기 3문장 쓰기" → info에 오늘 사용할 표현 패턴 3개 제시
  * "플랭크 3세트 30초" → info에 자세 설명과 초보자 팁 제공
  * "코드 전환 연습 15분" → info에 연습할 코드 진행과 BPM 안내

[금지 사항]
- URL, 링크, 유튜브 주소, 블로그 주소 등 외부 링크를 절대 포함하지 마세요
- 앱 이름이나 교재 이름은 괜찮지만, URL은 금지입니다

[출력 형식]
{"missions":[{"content":"미션내용","info":"미션 수행에 필요한 실제 정보/콘텐츠","difficulty":1}]}`;

function buildPrompt(scenario) {
  const parseMinutes = (input) => {
    const m1 = input.match(/(\d+)\s*시간(?:\s*(\d+)\s*분)?/);
    if (m1) return parseInt(m1[1]) * 60 + (parseInt(m1[2]) || 0);
    const m2 = input.match(/(\d+)\s*분/);
    if (m2) return parseInt(m2[1]);
    return null;
  };
  const minutes = parseMinutes(scenario.dailyTime);

  let prompt = `목표: ${scenario.goal}
사용자 정보: ${scenario.info}
하루 투자 가능 시간: ${scenario.dailyTime}
목표 기간: ${scenario.targetPeriod}

난이도 1~5 각각 1개씩 미션을 추천하고, 각 미션의 info에 수행에 필요한 실제 정보를 담아주세요.
`;

  if (minutes) {
    prompt += `\n시간 예산: 약 ${minutes}분 이내\n`;
    if (minutes <= 60) {
      prompt += `난이도 1은 5~10분, 난이도 2는 10~20분, 난이도 3은 20~35분, 난이도 4는 35~50분, 난이도 5는 50~60분\n`;
    }
  }

  prompt += `
난이도 기준:
- 난이도 1: 바로 시작할 수 있는 아주 작은 행동 1개
- 난이도 2: 짧은 실습 1개
- 난이도 3: 집중해서 끝낼 수 있는 중간 작업 1개
- 난이도 4: 도전적이지만 하루 안에 닫히는 작업 1개
- 난이도 5: 하루 시간 안에서 가장 어려운 작업 1개`;

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
          /"content"\s*:\s*"([^"]*)"\s*,\s*"info"\s*:\s*"([^"]*)"\s*,\s*"difficulty"\s*:\s*(\d)/g
        ),
      ];
      if (blocks.length > 0) {
        return {
          missions: blocks.map((m) => ({
            content: m[1],
            info: m[2],
            difficulty: parseInt(m[3]),
          })),
        };
      }
      return null;
    }
  } catch {
    return null;
  }
}

async function runScenario(scenario, provider, claudeModel) {
  const prompt = buildPrompt(scenario);
  const label = claudeModel ? `${provider.toUpperCase()} (${claudeModel})` : provider.toUpperCase();

  console.log(`\n${"─".repeat(60)}`);
  console.log(`${scenario.name} | ${label}`);
  console.log(`${"─".repeat(60)}`);

  const startTime = Date.now();
  const generate = provider === "clova" ? clovaGenerateText : claudeGenerateText;
  const result = await generate(prompt, INFO_SYSTEM, claudeModel ? { model: claudeModel } : {});
  const elapsed = Date.now() - startTime;

  const parsed = parseResponse(result.content);
  const missions = parsed?.missions || [];
  const valid = missions.length === 5 && new Set(missions.map((m) => m.difficulty)).size === 5;
  const hasInfo = missions.every((m) => m.info && m.info.length > 10);

  if (valid) {
    console.log(`통과 | info ${hasInfo ? "OK" : "부족"} (${elapsed}ms)`);
    for (const m of missions.sort((a, b) => a.difficulty - b.difficulty)) {
      console.log(`  난이도${m.difficulty}: ${m.content}`);
      console.log(`    [info] ${m.info || "(없음)"}`);
    }
  } else {
    console.log(`실패 (${elapsed}ms)`);
  }

  return {
    scenario: scenario.name,
    provider: label,
    goal: scenario.goal,
    userInfo: scenario.info,
    dailyTime: scenario.dailyTime,
    targetPeriod: scenario.targetPeriod,
    missions,
    valid,
    hasInfo,
    elapsed,
  };
}

function generateMarkdown(provider, results, timestamp) {
  const passed = results.filter((r) => r.valid).length;
  const infoOk = results.filter((r) => r.hasInfo).length;
  const avg = Math.round(results.reduce((s, r) => s + r.elapsed, 0) / results.length);

  let md = `# ${provider.toUpperCase()} 미션추천 + 미션정보(info) 테스트\n\n`;
  md += `- 실행일시: ${timestamp.replace("_", " ")}\n`;
  md += `- Provider: **${provider.toUpperCase()}**\n`;
  md += `- 검증 통과: **${passed}/${results.length}**\n`;
  md += `- 미션정보 포함: **${infoOk}/${results.length}**\n`;
  md += `- 평균 응답시간: **${avg}ms**\n\n---\n\n`;

  for (const r of results) {
    md += `## ${r.valid ? "PASS" : "FAIL"} | ${r.scenario} (${r.elapsed}ms)\n\n`;
    md += `| 항목 | 내용 |\n|------|------|\n`;
    md += `| 목표 | ${r.goal} |\n`;
    md += `| 사용자 정보 | ${r.userInfo} |\n`;
    md += `| 하루 시간 | ${r.dailyTime} |\n`;
    md += `| 목표 기간 | ${r.targetPeriod} |\n\n`;

    if (r.missions.length > 0) {
      for (const m of r.missions.sort((a, b) => a.difficulty - b.difficulty)) {
        md += `### 난이도 ${m.difficulty}: ${m.content}\n\n`;
        md += `**미션 정보:**\n${m.info || "-"}\n\n`;
      }
    }
  }
  return md;
}

async function main() {
  const now = new Date();
  const pad = (n) => String(n).padStart(2, "0");
  const ts = `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}_${pad(now.getHours())}-${pad(now.getMinutes())}-${pad(now.getSeconds())}`;

  const allResults = [];

  const providers = [
    { key: "clova", label: "CLOVA", claudeModel: null },
    { key: "claude", label: "CLAUDE OPUS", claudeModel: "claude-opus-4-6" },
    { key: "claude", label: "CLAUDE SONNET", claudeModel: "claude-sonnet-4-6" },
  ];

  for (const { key, label, claudeModel } of providers) {
    console.log(`\n${"=".repeat(60)}`);
    console.log(`미션 + 미션정보 테스트 (${label})`);
    console.log(`${"=".repeat(60)}`);

    const results = [];
    for (const scenario of SCENARIOS) {
      results.push(await runScenario(scenario, key, claudeModel));
    }

    const dirName = claudeModel ? `${key}_${claudeModel.includes("sonnet") ? "sonnet" : "opus"}` : key;
    const dir = path.join(__dirname, "results", dirName);
    fs.mkdirSync(dir, { recursive: true });
    const filename = `method1_info_${ts}.md`;
    fs.writeFileSync(path.join(dir, filename), generateMarkdown(label, results, ts), "utf-8");
    console.log(`\n결과 저장: results/${dirName}/${filename}`);
    allResults.push({ provider: label, results });
  }

  console.log(`\n${"=".repeat(60)}`);
  console.log("결과 요약");
  console.log(`${"=".repeat(60)}\n`);
  for (const { provider, results } of allResults) {
    const passed = results.filter((r) => r.valid).length;
    const infoOk = results.filter((r) => r.hasInfo).length;
    const avg = Math.round(results.reduce((s, r) => s + r.elapsed, 0) / results.length);
    console.log(`[${provider.toUpperCase()}] 통과: ${passed}/${results.length} | info: ${infoOk}/${results.length} | 평균: ${avg}ms`);
    for (const r of results) {
      console.log(`  ${r.valid ? "O" : "X"} ${r.scenario} (${r.elapsed}ms) ${r.hasInfo ? "info OK" : "info 부족"}`);
    }
    console.log();
  }
}

main().catch((err) => { console.error(err); process.exit(1); });
