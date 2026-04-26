import { query } from "@anthropic-ai/claude-agent-sdk";
import { writeFileSync } from "fs";

// ===== 설정 =====
const MODEL = process.argv[2] || "sonnet";
const EXP = { 1: 50, 2: 100, 3: 150, 4: 200, 5: 250 };

const SYSTEM_PROMPT = `당신은 미션 추천 AI입니다.

【필수 규칙】
1. difficulty는 1, 2, 3, 4, 5 각각 정확히 1번씩만 사용 (중복 금지)
2. content는 10-30자, 반드시 한국어로만 작성
3. JSON만 출력 (마크다운, 설명 금지)

【출력 형식】
{"missions":[{"content":"미션1","relatedInterest":["대","중","소"],"difficulty":1},{"content":"미션2","relatedInterest":["대","중","소"],"difficulty":2},{"content":"미션3","relatedInterest":["대","중","소"],"difficulty":3},{"content":"미션4","relatedInterest":["대","중","소"],"difficulty":4},{"content":"미션5","relatedInterest":["대","중","소"],"difficulty":5}]}`;

// ===== 20개 테스트 케이스 (학생 7, 취준생 7, 자영업 6) =====
const TEST_CASES = [
  // ===== 학생 (7개) =====
  { id: 1, category: "학생", subCategory: "고등학생", goal: "수능 국어 1등급 받고 싶어요", desiredOutcome: "비문학 독해 정답률 90% 이상", skillLevel: "중급", recentExperience: "모의고사 국어 2등급 나왔어요", targetPeriod: "5개월", dailyAvailableTime: "1시간 30분", additionalOpinion: "비문학이 특히 약해요" },
  { id: 2, category: "학생", subCategory: "대학생", goal: "AI 프로젝트 포트폴리오를 만들고 싶어요", desiredOutcome: "ChatGPT API 활용 웹앱 1개 완성", skillLevel: "기초", recentExperience: "파이썬 기초 문법 배웠고 판다스 조금 써봤어요", targetPeriod: "2개월", dailyAvailableTime: "2시간", additionalOpinion: "LangChain도 배워보고 싶어요" },
  { id: 3, category: "학생", subCategory: "대학생", goal: "OPIC IH 이상 받고 싶어요", desiredOutcome: "OPIC IH 등급 성적표", skillLevel: "기초", recentExperience: "영어 회화 학원 2개월 다녔어요", targetPeriod: "3개월", dailyAvailableTime: "1시간", additionalOpinion: "스크립트 암기보다 실전 연습 위주로 하고 싶어요" },
  { id: 4, category: "학생", subCategory: "대학원생", goal: "데이터 분석 논문을 쓰고 싶어요", desiredOutcome: "R로 회귀분석 완료하고 결과 챕터 초안 작성", skillLevel: "중급", recentExperience: "R로 기술통계 분석은 해봤어요", targetPeriod: "4개월", dailyAvailableTime: "3시간", additionalOpinion: "통계 해석이 어려워요" },
  { id: 5, category: "학생", subCategory: "중·고등학생", goal: "코딩 대회 입상하고 싶어요", desiredOutcome: "백준 골드 티어 달성", skillLevel: "기초", recentExperience: "파이썬으로 백준 실버 문제 몇 개 풀어봤어요", targetPeriod: "6개월", dailyAvailableTime: "1시간", additionalOpinion: "DP랑 그래프 탐색이 어려워요" },
  { id: 6, category: "학생", subCategory: "대학생", goal: "유튜브 채널을 시작하고 싶어요", desiredOutcome: "영상 10개 업로드하고 구독자 100명", skillLevel: "입문", recentExperience: "아직 해본 적 없음", targetPeriod: "3개월", dailyAvailableTime: "1시간 30분", additionalOpinion: "브이로그 말고 지식 콘텐츠를 만들고 싶어요" },
  { id: 7, category: "학생", subCategory: "휴학생", goal: "앱 개발을 배워서 창업 준비하고 싶어요", desiredOutcome: "Flutter로 MVP 앱 1개 출시", skillLevel: "기초", recentExperience: "Dart 문법 공부하고 간단한 UI 만들어봤어요", targetPeriod: "4개월", dailyAvailableTime: "4시간", additionalOpinion: "Firebase 연동도 해보고 싶어요" },

  // ===== 취준생 (7개) =====
  { id: 8, category: "취준생", subCategory: "포트폴리오 준비중", goal: "데이터 분석가로 취업하고 싶어요", desiredOutcome: "캐글 프로젝트 2개 포함 포트폴리오 완성", skillLevel: "중급", recentExperience: "파이썬으로 EDA 프로젝트 1개 해봤어요", targetPeriod: "3개월", dailyAvailableTime: "3시간", additionalOpinion: "SQL도 같이 준비해야 해요" },
  { id: 9, category: "취준생", subCategory: "자격증 준비중", goal: "SQLD 자격증 따고 싶어요", desiredOutcome: "SQLD 합격", skillLevel: "기초", recentExperience: "SELECT 쿼리 정도는 써봤어요", targetPeriod: "2개월", dailyAvailableTime: "1시간 30분", additionalOpinion: "실기 문제 유형이 어려워요" },
  { id: 10, category: "취준생", subCategory: "공채 준비중", goal: "공기업 NCS 필기 합격하고 싶어요", desiredOutcome: "NCS 모의고사 80점 이상", skillLevel: "기초", recentExperience: "NCS 책 사놓고 아직 거의 못 봤어요", targetPeriod: "3개월", dailyAvailableTime: "2시간", additionalOpinion: "수리 영역이 특히 약해요" },
  { id: 11, category: "취준생", subCategory: "이직 준비중", goal: "프론트엔드 개발자로 이직하고 싶어요", desiredOutcome: "React 포트폴리오 사이트와 이력서 완성", skillLevel: "중급", recentExperience: "Vue.js로 2년 정도 실무 했어요", targetPeriod: "2개월", dailyAvailableTime: "1시간 30분", additionalOpinion: "TypeScript도 같이 공부하고 싶어요" },
  { id: 12, category: "취준생", subCategory: "인턴/체험형", goal: "마케팅 인턴에 합격하고 싶어요", desiredOutcome: "마케팅 케이스 스터디 3개와 자기소개서", skillLevel: "입문", recentExperience: "마케팅 관련 수업 들었고 팀 프로젝트로 SNS 운영해봤어요", targetPeriod: "2개월", dailyAvailableTime: "2시간", additionalOpinion: "퍼포먼스 마케팅 쪽으로 가고 싶어요" },
  { id: 13, category: "취준생", subCategory: "포트폴리오 준비중", goal: "영상 편집자로 취업하고 싶어요", desiredOutcome: "프리미어 프로 편집 작품 5개 포트폴리오", skillLevel: "기초", recentExperience: "유튜브 보면서 간단한 브이로그 편집해봤어요", targetPeriod: "3개월", dailyAvailableTime: "2시간 30분", additionalOpinion: "모션그래픽도 배우고 싶어요" },
  { id: 14, category: "취준생", subCategory: "공채 준비중", goal: "금융권 취업하고 싶어요", desiredOutcome: "금융 NCS + 경제 상식 정리 노트 완성", skillLevel: "기초", recentExperience: "경제학 전공이고 증권사 인턴 1회 했어요", targetPeriod: "4개월", dailyAvailableTime: "2시간", additionalOpinion: "면접에서 시사 경제 질문 대비도 하고 싶어요" },

  // ===== 자영업 (6개) =====
  { id: 15, category: "자영업", subCategory: "카페/음식점 운영", goal: "네이버 플레이스 상위 노출시키고 싶어요", desiredOutcome: "리뷰 50개 이상, 검색 상위 3위 진입", skillLevel: "입문", recentExperience: "플레이스 등록만 해놨어요", targetPeriod: "2개월", dailyAvailableTime: "30분", additionalOpinion: "영수증 리뷰 이벤트도 해보고 싶어요" },
  { id: 16, category: "자영업", subCategory: "온라인 쇼핑몰", goal: "쿠팡 로켓그로스로 매출 올리고 싶어요", desiredOutcome: "쿠팡 입점 완료하고 월 매출 200만원", skillLevel: "기초", recentExperience: "스마트스토어에서 월 50만원 정도 팔고 있어요", targetPeriod: "3개월", dailyAvailableTime: "1시간", additionalOpinion: "광고비 효율적으로 쓰는 법이 궁금해요" },
  { id: 17, category: "자영업", subCategory: "프리랜서", goal: "AI 도구 활용해서 작업 효율을 높이고 싶어요", desiredOutcome: "ChatGPT+Midjourney 활용 워크플로우 구축", skillLevel: "기초", recentExperience: "ChatGPT로 간단한 카피라이팅 해봤어요", targetPeriod: "1개월", dailyAvailableTime: "1시간", additionalOpinion: "디자인 작업에 AI를 쓰고 싶어요" },
  { id: 18, category: "자영업", subCategory: "소규모 매장 운영", goal: "인스타 릴스로 매장 홍보하고 싶어요", desiredOutcome: "릴스 20개 업로드, 조회수 1만 이상 1개", skillLevel: "입문", recentExperience: "인스타 피드 사진만 가끔 올려요", targetPeriod: "2개월", dailyAvailableTime: "30분", additionalOpinion: "캡컷으로 편집하고 싶어요" },
  { id: 19, category: "자영업", subCategory: "1인 사업자", goal: "전자책 출간해서 부수입 만들고 싶어요", desiredOutcome: "크몽/탈잉에 전자책 1권 출간", skillLevel: "입문", recentExperience: "블로그 글을 꾸준히 쓰고 있어요", targetPeriod: "2개월", dailyAvailableTime: "1시간", additionalOpinion: "노션으로 원고 정리하고 싶어요" },
  { id: 20, category: "자영업", subCategory: "카페/음식점 운영", goal: "배달앱 평점 관리하고 매출 올리고 싶어요", desiredOutcome: "배민 평점 4.8 이상, 리뷰 답글 100% 달성", skillLevel: "기초", recentExperience: "배민에 입점해서 주문 받고 있는데 평점이 4.3이에요", targetPeriod: "2개월", dailyAvailableTime: "30분", additionalOpinion: "악성 리뷰 대응법도 알고 싶어요" },
];

// ===== 시간 파싱 =====
function extractMinutes(input) {
  const m1 = input.match(/(\d+)\s*시간(?:\s*(\d+)\s*분)?/);
  if (m1) return parseInt(m1[1]) * 60 + (parseInt(m1[2]) || 0);
  const m2 = input.match(/(\d+)\s*분/);
  if (m2) return parseInt(m2[1]);
  return null;
}

// ===== additionalContext =====
function buildAdditionalContext(tc) {
  const lines = [
    `현재 목표: ${tc.goal}`,
    `최종 결과물: ${tc.desiredOutcome}`,
    `현재 실력: ${tc.skillLevel}`,
    `최근 직접 해본 작업: ${tc.recentExperience}`,
    `목표 기간: ${tc.targetPeriod}`,
    `하루 투자 가능 시간: ${tc.dailyAvailableTime}`,
    `미션 원칙: 하루에 한 번 끝낼 수 있는 단일 작업만 추천`,
  ];
  const minutes = extractMinutes(tc.dailyAvailableTime);
  if (minutes) lines.push(`하루 미션 시간 상한: 약 ${minutes}분 이내`);
  if (tc.additionalOpinion) lines.push(`추가 의견: ${tc.additionalOpinion}`);
  return lines.join("\n");
}

// ===== 유저 프롬프트 =====
function buildUserPrompt(tc) {
  const additionalContext = buildAdditionalContext(tc);
  const minutes = extractMinutes(tc.dailyAvailableTime);
  const jsonExample = [1,2,3,4,5].map(d =>
    `{"content": "미션내용", "relatedInterest": ["대분류", "중분류"], "difficulty": ${d}}`
  ).join(",\n    ");

  let prompt = `사용자 정보: 직업: ${tc.category}, 직업상세: ${tc.subCategory}
관심사: [${tc.category} > ${tc.subCategory}]

===== 생성 요청 =====
난이도 1, 2, 3, 4, 5 각각 1개씩, 총 5개의 미션을 생성하세요.

===== 추가 사용자 문맥 =====
${additionalContext}

위 문맥을 반드시 반영해서 미션을 생성하세요.
- 현재 실력에 맞게 현실적인 수준으로 제안하세요.
- 하루 투자 가능 시간을 크게 넘지 않도록 하세요.
- 목표 기간 안에 도달할 수 있는 단계형 미션으로 제안하세요.
`;

  if (minutes) {
    prompt += `\n===== 시간 예산 엄수 =====
- 사용자의 하루 미션 시간 상한은 약 ${minutes}분입니다.
- 난이도 5도 이 시간 안에서 끝나야 합니다.
- 한 번에 산출물 1개만 요구하세요.`;
    if (minutes <= 60) {
      prompt += `
- 난이도 1은 5~10분, 난이도 2는 10~20분, 난이도 3은 20~35분, 난이도 4는 35~50분, 난이도 5는 50~60분 안에 끝나는 작업으로 제안하세요.`;
    } else if (minutes <= 90) {
      prompt += `
- 90분 이하 사용자에게는 프로젝트 단계 전체나 문서 묶음 작업을 제안하지 마세요.`;
    }
    prompt += "\n";
  }

  prompt += `
===== 난이도 기준 (모든 난이도는 하루 안에 완료 가능해야 함) =====
- 난이도 1: 오늘 바로 시작할 수 있는 아주 작은 행동 1개
- 난이도 2: 짧은 실습 또는 예제 수행 1개
- 난이도 3: 집중해서 끝낼 수 있는 중간 크기 작업 1개
- 난이도 4: 다소 도전적이지만 하루 안에 닫히는 단일 작업 1개
- 난이도 5: 그날 할 수 있는 가장 어려운 작업이지만, 여전히 하루 투자 가능 시간 안에서 끝나는 작업 1개

===== 범위 제한 (매우 중요) =====
- 각 미션은 "오늘 할 1개 행동"이어야 합니다.
- 프로젝트 전체를 끝내는 표현을 절대 쓰지 마세요.
- 숫자, 개수, 시간, 화면 수 등으로 범위를 드러내세요.
- 여러 산출물을 한 번에 요구하지 마세요.
- 문장 안에서 '후', '및', '+', '&', '그리고'를 사용해 여러 행동을 묶지 마세요.

===== 좋은 미션 (필수) =====
✅ 구체적이고 측정 가능 (횟수, 시간, 개수 등 수치 포함)
✅ 하루 안에 완료 가능

===== 나쁜 미션 (금지) =====
❌ 모호함: "운동하기", "공부하기"
❌ 장기 목표: "한 달간 다이어트"
❌ 범위 과다: "MVP 핵심 기능 검증용 사용자 테스트 진행"

===== 응답 형식 (JSON만 출력) =====
\`\`\`json
{
  "missions": [
    ${jsonExample}
  ]
}
\`\`\``;

  return prompt.trim();
}

// ===== JSON 파싱 =====
function extractJson(text) {
  let cleaned = text.replace(/```json\s*/gi, "").replace(/```\s*/g, "").replace(/\s*```/g, "").trim();
  const match = cleaned.match(/\{[\s\S]*"missions"[\s\S]*\}/);
  return match ? match[0] : cleaned;
}

// ===== Claude agent-sdk 호출 =====
async function recommendMissions(tc) {
  const userPrompt = buildUserPrompt(tc);
  const maxRetries = 3;

  for (let attempt = 1; attempt <= maxRetries; attempt++) {
    try {
      const conversation = query({
        prompt: userPrompt,
        options: {
          systemPrompt: SYSTEM_PROMPT,
          model: MODEL,
          maxTurns: 1,
          allowedTools: [],
        },
      });

      let resultText = "";
      for await (const msg of conversation) {
        if (msg.type === "result" && msg.subtype === "success") {
          resultText = msg.result;
        }
      }

      if (!resultText) {
        console.log(`  ⚠️ 빈 응답 (시도 ${attempt})`);
        continue;
      }

      const jsonStr = extractJson(resultText);
      const parsed = JSON.parse(jsonStr);
      const missions = parsed.missions || [];

      const diffs = missions.map(m => m.difficulty).filter(Boolean);
      const expected = new Set([1, 2, 3, 4, 5]);
      const actual = new Set(diffs);
      if (actual.size === 5 && [...expected].every(d => actual.has(d))) {
        return missions.sort((a, b) => a.difficulty - b.difficulty);
      }
      console.log(`  ⚠️ 난이도 검증 실패 (시도 ${attempt}): [${diffs}]`);
    } catch (e) {
      console.log(`  ❌ 시도 ${attempt} 실패: ${e.message}`);
    }
  }
  return null;
}

// ===== 메인 =====
async function main() {
  const modelLabel = MODEL === "opus" ? "Claude Opus" : MODEL === "haiku" ? "Claude Haiku" : "Claude Sonnet";
  const now = new Date().toLocaleString("ko-KR", { timeZone: "Asia/Seoul" });
  const lines = [];

  lines.push(`# 챗봇 미션 추천 테스트 (학생/취준생/자영업 집중)`);
  lines.push("");
  lines.push(`> LLM: **${modelLabel} (claude-agent-sdk)**`);
  lines.push(`> 테스트 일시: ${now}`);
  lines.push(`> 대상: 학생 7, 취준생 7, 자영업 6 (총 20케이스)`);
  lines.push(`> 추천 난이도: 1~5 (각 1개씩, 총 5개/케이스)`);
  lines.push("");
  lines.push("---");
  lines.push("");

  let success = 0, fail = 0;

  for (let i = 0; i < TEST_CASES.length; i++) {
    const tc = TEST_CASES[i];
    const label = `${tc.category} / ${tc.subCategory}`;
    console.log(`[${MODEL}] [${i + 1}/${TEST_CASES.length}] TC-${String(tc.id).padStart(2, "0")} ${label} ...`);

    lines.push(`## TC-${String(tc.id).padStart(2, "0")}. ${label}`);
    lines.push("");
    lines.push(`| 항목 | 내용 |`);
    lines.push(`|------|------|`);
    lines.push(`| 목표 | ${tc.goal} |`);
    lines.push(`| 결과물 | ${tc.desiredOutcome} |`);
    lines.push(`| 실력 | ${tc.skillLevel} |`);
    lines.push(`| 최근 경험 | ${tc.recentExperience} |`);
    lines.push(`| 기간 | ${tc.targetPeriod} |`);
    lines.push(`| 하루 시간 | ${tc.dailyAvailableTime} |`);
    if (tc.additionalOpinion) lines.push(`| 추가 | ${tc.additionalOpinion} |`);
    lines.push("");

    const missions = await recommendMissions(tc);

    if (missions) {
      lines.push("| 난이도 | 미션 내용 | 경험치 |");
      lines.push("|--------|----------|--------|");
      for (const m of missions) {
        lines.push(`| ${m.difficulty} | ${m.content} | ${EXP[m.difficulty] || 0} |`);
      }
      lines.push("");
      success++;
      console.log(`  ✅ 5개 미션 추천 완료`);
    } else {
      lines.push("⚠️ 추천된 미션 없음");
      lines.push("");
      fail++;
      console.log(`  ❌ 실패`);
    }
  }

  lines.push("---");
  lines.push("");
  lines.push("## 요약");
  lines.push("");
  lines.push("| 항목 | 값 |");
  lines.push("|------|-----|");
  lines.push(`| LLM | ${modelLabel} |`);
  lines.push(`| 총 테스트 | ${TEST_CASES.length} |`);
  lines.push(`| 성공 | ${success} |`);
  lines.push(`| 실패 | ${fail} |`);

  const ts = new Date().toISOString().replace(/[-:T]/g, "").slice(0, 8);
  const resultsDir = `../scripts/test-chatbot-recommend/results/claude`;
  const outputFile = `${resultsDir}/${MODEL}_20cases_${ts}.md`;
  writeFileSync(outputFile, lines.join("\n"));
  console.log(`\n📄 결과 저장: ${outputFile}`);
}

main().catch(console.error);
