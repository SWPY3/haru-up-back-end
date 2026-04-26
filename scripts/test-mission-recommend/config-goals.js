/**
 * 구체적 목표 기반 테스트 시나리오
 * 토익 900점, 체중 감량 등 현실적인 목표들
 */

export { SYSTEM_PROMPT, validateMissionResponse } from "./config.js";

// ── 챗봇 미션추천용: 구체적 목표 시나리오 (10개) ──
export const CHATBOT_TEST_SCENARIOS = [
  {
    name: "대학생 - 토익 900점",
    context: {
      category: "자격증/시험",
      subCategory: "토익",
      goal: "토익 900점 이상 취득",
      desiredOutcome: "토익 900점 성적표로 졸업요건 충족",
      skillLevel: "현재 토익 750점, RC가 LC보다 높음",
      recentExperience: "모의고사를 풀어봤는데 시간이 부족했어요",
      targetPeriod: "3개월",
      dailyAvailableTime: "1시간 30분",
      additionalOpinion: "LC 파트3,4가 약해요",
    },
  },
  {
    name: "직장인 - 체중 5kg 감량",
    context: {
      category: "체력관리 및 운동",
      subCategory: "다이어트/체중관리",
      goal: "체중 5kg 감량",
      desiredOutcome: "현재 75kg에서 70kg으로 감량",
      skillLevel: "운동 경험 거의 없음, 식단 조절도 처음",
      recentExperience: "일주일 동안 저녁 안 먹어봤는데 3일 만에 포기",
      targetPeriod: "3개월",
      dailyAvailableTime: "40분",
      additionalOpinion: "점심은 회사 구내식당이라 조절이 어려워요",
    },
  },
  {
    name: "고3 - 수능 수학 1등급",
    context: {
      category: "학업/시험",
      subCategory: "수능 준비",
      goal: "수능 수학 1등급 달성",
      desiredOutcome: "수능 수학 90점 이상",
      skillLevel: "현재 모의고사 수학 2등급, 킬러 문항에서 실수",
      recentExperience: "3월 모의고사에서 85점 받았어요",
      targetPeriod: "8개월",
      dailyAvailableTime: "2시간",
      additionalOpinion: "확률과 통계 단원이 특히 약해요",
    },
  },
  {
    name: "30대 - 마라톤 풀코스 완주",
    context: {
      category: "체력관리 및 운동",
      subCategory: "러닝/마라톤",
      goal: "마라톤 풀코스(42.195km) 완주",
      desiredOutcome: "서울마라톤 풀코스 5시간 이내 완주",
      skillLevel: "10km까지는 뛸 수 있음, 하프는 미경험",
      recentExperience: "주 2회 5km 러닝 중, 최근 10km 58분 기록",
      targetPeriod: "6개월",
      dailyAvailableTime: "1시간",
      additionalOpinion: "장거리 뛰면 무릎이 아파요",
    },
  },
  {
    name: "주부 - 운전면허 취득",
    context: {
      category: "자격증/시험",
      subCategory: "운전면허",
      goal: "운전면허 1종 보통 취득",
      desiredOutcome: "운전면허증 발급 완료",
      skillLevel: "완전 초보, 운전대를 잡아본 적 없음",
      recentExperience: "학원 등록만 해놓은 상태",
      targetPeriod: "2개월",
      dailyAvailableTime: "1시간",
      additionalOpinion: null,
    },
  },
  {
    name: "직장인 - 영어 프레젠테이션",
    context: {
      category: "외국어 공부",
      subCategory: "비즈니스 영어",
      goal: "영어로 15분 프레젠테이션 하기",
      desiredOutcome: "다음 분기 글로벌 미팅에서 영어 발표 성공",
      skillLevel: "영어 읽기는 중급이지만 말하기가 매우 부족",
      recentExperience: "영어 이메일은 번역기 도움 받아서 보내고 있어요",
      targetPeriod: "2개월",
      dailyAvailableTime: "30분",
      additionalOpinion: "발음보다 자신감이 문제예요",
    },
  },
  {
    name: "대학생 - 코딩테스트 합격",
    context: {
      category: "학업/시험",
      subCategory: "코딩테스트",
      goal: "대기업 코딩테스트 통과",
      desiredOutcome: "백준 골드 수준 문제 풀이 가능",
      skillLevel: "백준 실버3 수준, 기초 알고리즘만 알고 있음",
      recentExperience: "프로그래머스 레벨2 문제를 반 정도 풀 수 있어요",
      targetPeriod: "4개월",
      dailyAvailableTime: "1시간 30분",
      additionalOpinion: "그래프/DP가 약해요",
    },
  },
  {
    name: "40대 - 복부지방 감소",
    context: {
      category: "체력관리 및 운동",
      subCategory: "체형관리",
      goal: "허리둘레 5cm 줄이기",
      desiredOutcome: "허리둘레 90cm에서 85cm으로 감소",
      skillLevel: "가벼운 산책만 하고 있음",
      recentExperience: "유튜브 복근 운동 따라했는데 3일 만에 허리 아팠어요",
      targetPeriod: "4개월",
      dailyAvailableTime: "30분",
      additionalOpinion: "허리디스크 병력이 있어서 조심해야 해요",
    },
  },
  {
    name: "취준생 - SQLD 자격증",
    context: {
      category: "자격증/시험",
      subCategory: "IT 자격증",
      goal: "SQLD 자격증 취득",
      desiredOutcome: "SQLD 시험 합격",
      skillLevel: "SQL SELECT문 정도만 알고 있음",
      recentExperience: "학교 수업에서 기본 쿼리 작성해봤습니다",
      targetPeriod: "2개월",
      dailyAvailableTime: "2시간",
      additionalOpinion: "이론보다 실습 위주로 공부하고 싶어요",
    },
  },
  {
    name: "직장인 - 매일 독서 습관",
    context: {
      category: "자기계발",
      subCategory: "독서",
      goal: "한 달에 책 3권 완독",
      desiredOutcome: "3개월 동안 9권 읽고 독서 노트 작성",
      skillLevel: "작년에 총 2권밖에 못 읽음",
      recentExperience: "출퇴근 지하철에서 전자책 시작했는데 자꾸 폰만 보게 돼요",
      targetPeriod: "3개월",
      dailyAvailableTime: "20분",
      additionalOpinion: "출퇴근 시간 활용하고 싶어요",
    },
  },
];

// ── 직접입력 목표 기반 미션추천 테스트 시나리오 (20개) ──
// 소분류(3번째)를 시스템 값 대신 사용자가 직접 타이핑한 목표로 설정
export const INTEREST_TEST_SCENARIOS = [
  // ── 운동/체력 (5개) ──
  {
    name: "헬스 - 살 3kg 빼기",
    interests: ["체력관리 및 운동", "헬스", "살 3kg 빼기"],
    memberProfile: { age: 28, gender: "FEMALE", jobName: "직장인", jobDetailName: null },
  },
  {
    name: "러닝 - 하프마라톤 완주",
    interests: ["체력관리 및 운동", "러닝", "하프마라톤 완주하기"],
    memberProfile: { age: 32, gender: "MALE", jobName: "직장인", jobDetailName: null },
  },
  {
    name: "수영 - 자유형 500m 완영",
    interests: ["체력관리 및 운동", "수영", "자유형 500m 쉬지않고 완영"],
    memberProfile: { age: 27, gender: "FEMALE", jobName: null, jobDetailName: null },
  },
  {
    name: "요가 - 허리통증 개선",
    interests: ["체력관리 및 운동", "요가", "허리통증 개선하기"],
    memberProfile: { age: 45, gender: "FEMALE", jobName: "직장인", jobDetailName: "사무직" },
  },
  {
    name: "헬스 - 벤치프레스 80kg 달성",
    interests: ["체력관리 및 운동", "헬스", "벤치프레스 80kg 달성"],
    memberProfile: { age: 25, gender: "MALE", jobName: "학생", jobDetailName: "대학생" },
  },

  // ── 외국어 (4개) ──
  {
    name: "영어 - 토익 900점 달성",
    interests: ["외국어 공부", "영어", "토익 900점 달성"],
    memberProfile: { age: 24, gender: "MALE", jobName: "학생", jobDetailName: "대학생" },
  },
  {
    name: "영어 - 영어회의 자신감 갖기",
    interests: ["외국어 공부", "영어", "영어 회의에서 자신감 갖기"],
    memberProfile: { age: 33, gender: "FEMALE", jobName: "직장인", jobDetailName: "IT" },
  },
  {
    name: "일본어 - JLPT N2 합격",
    interests: ["외국어 공부", "일본어", "JLPT N2 합격"],
    memberProfile: { age: 26, gender: "MALE", jobName: "학생", jobDetailName: "대학원생" },
  },
  {
    name: "중국어 - 출장 기초회화",
    interests: ["외국어 공부", "중국어", "중국 출장에서 기본 대화하기"],
    memberProfile: { age: 35, gender: "MALE", jobName: "직장인", jobDetailName: "무역" },
  },

  // ── 자기계발/학습 (4개) ──
  {
    name: "독서 - 월 4권 읽기",
    interests: ["자기계발", "독서", "한 달에 책 4권 읽기"],
    memberProfile: { age: 29, gender: "FEMALE", jobName: "직장인", jobDetailName: null },
  },
  {
    name: "코딩 - 사이드프로젝트 완성",
    interests: ["자기계발", "프로그래밍", "개인 사이드 프로젝트 완성하기"],
    memberProfile: { age: 27, gender: "MALE", jobName: "학생", jobDetailName: "컴퓨터공학" },
  },
  {
    name: "자격증 - 정보처리기사 합격",
    interests: ["자격증/시험", "IT자격증", "정보처리기사 합격하기"],
    memberProfile: { age: 25, gender: "MALE", jobName: "취준생", jobDetailName: null },
  },
  {
    name: "자격증 - 공인중개사 합격",
    interests: ["자격증/시험", "부동산", "공인중개사 시험 합격"],
    memberProfile: { age: 42, gender: "FEMALE", jobName: "자영업", jobDetailName: null },
  },

  // ── 재테크/투자 (2개) ──
  {
    name: "재테크 - 월 50만원 저축",
    interests: ["재테크 및 투자", "지출 관리 및 예산 세우기", "월 50만원 저축하기"],
    memberProfile: { age: 26, gender: "MALE", jobName: "직장인", jobDetailName: null },
  },
  {
    name: "투자 - 주식 기초 공부",
    interests: ["재테크 및 투자", "주식", "주식 기초부터 공부하기"],
    memberProfile: { age: 30, gender: "FEMALE", jobName: "직장인", jobDetailName: "마케팅" },
  },

  // ── 취미/생활 (3개) ──
  {
    name: "요리 - 매일 도시락 싸기",
    interests: ["취미/생활", "요리/베이킹", "매일 점심 도시락 직접 싸기"],
    memberProfile: { age: 28, gender: "MALE", jobName: "직장인", jobDetailName: null },
  },
  {
    name: "기타 - 좋아하는 노래 3곡 연주",
    interests: ["취미/생활", "음악/악기", "좋아하는 노래 3곡 기타로 치기"],
    memberProfile: { age: 22, gender: "FEMALE", jobName: "학생", jobDetailName: "대학생" },
  },
  {
    name: "그림 - 매일 드로잉 습관",
    interests: ["취미/생활", "미술/그림", "매일 1장씩 드로잉하는 습관 만들기"],
    memberProfile: { age: 24, gender: "FEMALE", jobName: "학생", jobDetailName: null },
  },

  // ── 마음건강 (1개) ──
  {
    name: "명상 - 불안감 줄이기",
    interests: ["마음건강/멘탈", "명상/마음챙김", "일상 불안감 줄이기"],
    memberProfile: { age: 31, gender: "MALE", jobName: "직장인", jobDetailName: "개발자" },
  },

  // ── 직무역량 (1개) ──
  {
    name: "발표 - 프레젠테이션 잘하기",
    interests: ["직무 관련 역량 개발", "커뮤니케이션", "프레젠테이션 자신감 갖기"],
    memberProfile: { age: 29, gender: "FEMALE", jobName: "직장인", jobDetailName: "기획" },
  },
];
