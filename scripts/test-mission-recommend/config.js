/**
 * 테스트 공통 설정
 */

// ── 시스템 프롬프트 (Clova/Claude 공통) ──
export const SYSTEM_PROMPT = `당신은 미션 추천 AI입니다.

【필수 규칙】
1. difficulty는 1, 2, 3, 4, 5 각각 정확히 1번씩만 사용 (중복 금지)
2. content는 10-30자, 반드시 한국어로만 작성
3. JSON만 출력 (마크다운, 설명 금지)

【출력 형식】
{"missions":[{"content":"미션1","relatedInterest":["대","중","소"],"difficulty":1},{"content":"미션2","relatedInterest":["대","중","소"],"difficulty":2},{"content":"미션3","relatedInterest":["대","중","소"],"difficulty":3},{"content":"미션4","relatedInterest":["대","중","소"],"difficulty":4},{"content":"미션5","relatedInterest":["대","중","소"],"difficulty":5}]}`;

// ── 챗봇 미션추천용 테스트 시나리오 (10개) ──
export const CHATBOT_TEST_SCENARIOS = [
  {
    name: "고등학생 - 일본어 독학",
    context: {
      category: "외국어 공부",
      subCategory: "일본어",
      goal: "JLPT N3 합격",
      desiredOutcome: "일본 여행에서 현지인과 간단한 대화 가능",
      skillLevel: "히라가나/가타카나만 읽을 수 있음, 문법은 거의 모름",
      recentExperience: "애니메이션으로 인사말 몇 개 외웠습니다",
      targetPeriod: "8개월",
      dailyAvailableTime: "40분",
      additionalOpinion: "한자가 어려워서 걱정이에요",
    },
  },
  {
    name: "주부 - 홈베이킹",
    context: {
      category: "취미/생활",
      subCategory: "요리/베이킹",
      goal: "집에서 빵과 디저트 만들기",
      desiredOutcome: "가족에게 매주 다른 빵을 직접 만들어 주기",
      skillLevel: "간단한 쿠키는 만들어봤지만 발효빵은 처음",
      recentExperience: "초코칩 쿠키를 레시피 보고 만들었는데 괜찮았어요",
      targetPeriod: "3개월",
      dailyAvailableTime: "1시간 30분",
      additionalOpinion: "오븐은 있는데 반죽기가 없어요",
    },
  },
  {
    name: "대학생 - 기타 독학",
    context: {
      category: "취미/생활",
      subCategory: "음악/악기",
      goal: "통기타로 노래 반주하기",
      desiredOutcome: "좋아하는 노래 5곡을 기타 치며 부르기",
      skillLevel: "완전 초보, 기타를 막 구입함",
      recentExperience: "유튜브로 코드 잡는 법 영상 하나 봤습니다",
      targetPeriod: "4개월",
      dailyAvailableTime: "30분",
      additionalOpinion: null,
    },
  },
  {
    name: "30대 직장인 - 러닝 습관",
    context: {
      category: "체력관리 및 운동",
      subCategory: "유산소 운동",
      goal: "10km 마라톤 완주",
      desiredOutcome: "10km를 1시간 이내에 뛸 수 있는 체력",
      skillLevel: "평소 운동 안 함, 2km만 뛰어도 힘듦",
      recentExperience: "동네 공원에서 가볍게 조깅을 시도했는데 1.5km에서 포기",
      targetPeriod: "5개월",
      dailyAvailableTime: "40분",
      additionalOpinion: "무릎이 약한 편이라 부상이 걱정돼요",
    },
  },
  {
    name: "프리랜서 - 유튜브 시작",
    context: {
      category: "콘텐츠 제작",
      subCategory: "영상 편집",
      goal: "유튜브 채널 개설 및 첫 영상 업로드",
      desiredOutcome: "주 1회 정기적으로 영상 업로드하는 채널 운영",
      skillLevel: "스마트폰 촬영만 가능, 편집 프로그램은 써본 적 없음",
      recentExperience: "인스타 릴스 30초짜리 한 번 만들어봤습니다",
      targetPeriod: "2개월",
      dailyAvailableTime: "1시간",
      additionalOpinion: "무료 편집 프로그램으로 시작하고 싶어요",
    },
  },
  {
    name: "40대 주부 - 자격증 취득",
    context: {
      category: "자격증/시험",
      subCategory: "사회복지사",
      goal: "사회복지사 2급 자격증 취득",
      desiredOutcome: "시험 합격 후 복지관 취업",
      skillLevel: "관련 학과를 졸업했지만 10년 넘게 공부를 안 함",
      recentExperience: "교재를 구입해서 1장까지 읽었습니다",
      targetPeriod: "6개월",
      dailyAvailableTime: "2시간",
      additionalOpinion: "아이 등원 후 오전 시간을 활용하려고 해요",
    },
  },
  {
    name: "대학생 - 독서 습관",
    context: {
      category: "자기계발",
      subCategory: "독서",
      goal: "한 달에 책 4권 읽기",
      desiredOutcome: "독서 습관을 들이고 독후감 기록 남기기",
      skillLevel: "작년에 책을 3권밖에 안 읽음",
      recentExperience: "자기계발서 한 권을 읽다가 중간에 포기했습니다",
      targetPeriod: "3개월",
      dailyAvailableTime: "30분",
      additionalOpinion: "집중력이 짧아서 긴 책은 힘들어요",
    },
  },
  {
    name: "50대 - 수채화 그리기",
    context: {
      category: "취미/생활",
      subCategory: "미술/그림",
      goal: "수채화로 풍경화 그리기",
      desiredOutcome: "집 거실에 걸 수 있는 수채화 작품 3점 완성",
      skillLevel: "어릴 때 미술학원 다닌 적 있지만 30년 넘게 안 그림",
      recentExperience: "색연필로 간단한 꽃 스케치를 해봤습니다",
      targetPeriod: "4개월",
      dailyAvailableTime: "1시간",
      additionalOpinion: "수채화 도구 세트는 구매했어요",
    },
  },
  {
    name: "직장인 - 중국어 입문",
    context: {
      category: "외국어 공부",
      subCategory: "중국어",
      goal: "중국어 기초 회화 가능",
      desiredOutcome: "중국 출장에서 택시 타고 식당 주문하기",
      skillLevel: "니하오, 셰셰 정도만 알고 있음",
      recentExperience: "중국어 학습 앱을 깔아서 성조 연습을 해봤습니다",
      targetPeriod: "6개월",
      dailyAvailableTime: "20분",
      additionalOpinion: "성조가 너무 어려워요",
    },
  },
  {
    name: "대학원생 - 명상 습관",
    context: {
      category: "마음건강/멘탈",
      subCategory: "명상/마음챙김",
      goal: "매일 명상으로 스트레스 관리",
      desiredOutcome: "논문 스트레스를 스스로 컨트롤할 수 있는 상태",
      skillLevel: "명상을 해본 적 없음",
      recentExperience: "수면 전 심호흡 5분을 시도해봤는데 잡생각이 많았어요",
      targetPeriod: "2개월",
      dailyAvailableTime: "15분",
      additionalOpinion: null,
    },
  },
  // ── 후속 추천 시나리오 (completedMissions 포함) ──
  {
    name: "[후속] 대학생 - 기타 독학 2일차",
    context: {
      category: "취미/생활",
      subCategory: "음악/악기",
      goal: "통기타로 노래 반주하기",
      desiredOutcome: "좋아하는 노래 5곡을 기타 치며 부르기",
      skillLevel: "완전 초보, 기타를 막 구입함",
      recentExperience: "유튜브로 코드 잡는 법 영상 하나 봤습니다",
      targetPeriod: "4개월",
      dailyAvailableTime: "30분",
      additionalOpinion: null,
      completedMissions: [
        { content: "기타 줄 이름 6개 외우기", difficulty: 1 },
        { content: "C코드 잡고 5초 유지 3회 반복", difficulty: 2 },
        { content: "C-Am 코드 전환 10회 연습", difficulty: 3 },
        { content: "메트로놈 60BPM에 맞춰 4비트 스트럼 5분", difficulty: 4 },
        { content: "좋아하는 노래 1절 코드 악보 보며 천천히 따라치기", difficulty: 5 },
      ],
    },
  },
  {
    name: "[후속] 주부 - 홈베이킹 2일차",
    context: {
      category: "취미/생활",
      subCategory: "요리/베이킹",
      goal: "집에서 빵과 디저트 만들기",
      desiredOutcome: "가족에게 매주 다른 빵을 직접 만들어 주기",
      skillLevel: "간단한 쿠키는 만들어봤지만 발효빵은 처음",
      recentExperience: "초코칩 쿠키를 레시피 보고 만들었는데 괜찮았어요",
      targetPeriod: "3개월",
      dailyAvailableTime: "1시간 30분",
      additionalOpinion: "오븐은 있는데 반죽기가 없어요",
      completedMissions: [
        { content: "발효빵 기초 용어 10개 정리", difficulty: 1 },
        { content: "밀가루 종류별 특징 비교표 작성", difficulty: 2 },
        { content: "손반죽으로 모닝롤 반죽 1회 연습", difficulty: 3 },
        { content: "손반죽 식빵 1덩이 완성", difficulty: 4 },
        { content: "크루아상 생지 3단 접기 연습 후 굽기", difficulty: 5 },
      ],
    },
  },
];

// ── 기존 관심사 기반 미션추천용 테스트 시나리오 (DB 실데이터 기준) ──
export const INTEREST_TEST_SCENARIOS = [
  {
    name: "헬스 - 근력 향상",
    interests: ["체력관리 및 운동", "헬스", "근력 향상"],
    memberProfile: { age: 30, gender: "MALE", jobName: null, jobDetailName: null },
  },
  {
    name: "헬스 - 근력 키우기",
    interests: ["체력관리 및 운동", "헬스", "근력 키우기"],
    memberProfile: { age: 30, gender: "MALE", jobName: "직장인", jobDetailName: "공채 준비중" },
  },
  {
    name: "러닝 - 체력 증진",
    interests: ["체력관리 및 운동", "러닝", "체력 증진"],
    memberProfile: { age: 31, gender: "MALE", jobName: null, jobDetailName: null },
  },
  {
    name: "자전거 - 체력 증진",
    interests: ["체력관리 및 운동", "자전거", "체력 증진"],
    memberProfile: { age: 31, gender: "MALE", jobName: null, jobDetailName: null },
  },
  {
    name: "영어 - 단어 학습",
    interests: ["외국어 공부", "영어", "단어 학습"],
    memberProfile: { age: null, gender: "MALE", jobName: null, jobDetailName: null },
  },
  {
    name: "리더십 - 성과관리 능력 향상",
    interests: ["직무 관련 역량 개발", "리더십", "성과관리 능력 향상"],
    memberProfile: { age: 30, gender: "MALE", jobName: "학생", jobDetailName: "영업/마케팅" },
  },
];

// ── 결과 검증 ──
export function validateMissionResponse(jsonStr) {
  const errors = [];

  let parsed;
  try {
    // markdown 코드블록 제거
    let cleaned = jsonStr.replace(/```json?\s*/g, "").replace(/```/g, "").trim();
    // Clova thinking 모드가 JSON 뒤에 설명을 붙이는 경우 → JSON 부분만 추출
    const match = cleaned.match(/\{[\s\S]*"missions"\s*:\s*\[[\s\S]*\]\s*\}/);
    if (match) cleaned = match[0];
    try {
      parsed = JSON.parse(cleaned);
    } catch {
      // Clova가 content 안에 이스케이프 안 된 특수문자(×, ·, 괄호 안 따옴표 등)를 넣는 경우
      // 또는 trailing comma 등 → 개별 미션 블록을 정규식으로 추출 시도
      const missionBlocks = [...cleaned.matchAll(/"content"\s*:\s*"([^"]*)"\s*,\s*"relatedInterest"\s*:\s*(\[[^\]]*\])\s*,\s*"difficulty"\s*:\s*(\d)/g)];
      if (missionBlocks.length > 0) {
        parsed = {
          missions: missionBlocks.map((m) => ({
            content: m[1],
            relatedInterest: JSON.parse(m[2]),
            difficulty: parseInt(m[3]),
          })),
        };
      } else {
        throw new Error("정규식 추출도 실패");
      }
    }
  } catch (e) {
    return { valid: false, errors: [`JSON 파싱 실패: ${e.message}`], parsed: null };
  }

  const missions = parsed.missions;
  if (!Array.isArray(missions)) {
    return { valid: false, errors: ["missions 배열 없음"], parsed };
  }

  if (missions.length !== 5) {
    errors.push(`미션 개수: ${missions.length} (기대: 5)`);
  }

  // difficulty 검증
  const difficulties = missions.map((m) => m.difficulty).sort();
  const expected = [1, 2, 3, 4, 5];
  if (JSON.stringify(difficulties) !== JSON.stringify(expected)) {
    errors.push(`difficulty 분포 오류: [${difficulties}] (기대: [1,2,3,4,5])`);
  }

  // content 검증
  for (const m of missions) {
    if (!m.content || typeof m.content !== "string") {
      errors.push(`content 누락 (difficulty ${m.difficulty})`);
      continue;
    }
  }

  // relatedInterest 보정 (검증은 하지 않음 — 백엔드에서 입력 directFullPath로 덮어쓰기 때문)
  // Clova가 ["외국어 공부>중국어>발음"] 처럼 > 구분자로 합쳐서 반환하는 경우 split
  for (const m of missions) {
    if (Array.isArray(m.relatedInterest) && m.relatedInterest.length === 1 && m.relatedInterest[0].includes(">")) {
      m.relatedInterest = m.relatedInterest[0].split(">").map((s) => s.trim());
    }
  }

  return { valid: errors.length === 0, errors, parsed };
}
