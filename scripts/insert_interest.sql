-- interest_embeddings 테이블에 초기 데이터 INSERT
-- 실행 방법: cat scripts/insert_interest.sql | docker exec -i -e PGPASSWORD={PASSWORD} postgres psql -U haruup_user -d haruup

-- 기존 데이터 삭제 (필요시)
-- TRUNCATE interest_embeddings RESTART IDENTITY CASCADE;

-- 1. 대분류 (MAIN) INSERT
INSERT INTO interest_embeddings (name, level, parent_id, full_path, embedding, usage_count, created_source, is_activated, deleted, created_at, updated_at)
VALUES
    ('외국어 공부', 'MAIN', NULL, ARRAY['외국어 공부'], NULL, 0, 'SYSTEM', true, false, NOW(), NOW()),
    ('재테크 및 투자', 'MAIN', NULL, ARRAY['재테크 및 투자'], NULL, 0, 'SYSTEM', true, false, NOW(), NOW()),
    ('체력관리 및 운동', 'MAIN', NULL, ARRAY['체력관리 및 운동'], NULL, 0, 'SYSTEM', true, false, NOW(), NOW()),
    ('자격증 공부', 'MAIN', NULL, ARRAY['자격증 공부'], NULL, 0, 'SYSTEM', true, false, NOW(), NOW()),
    ('직무 관련 역량 개발', 'MAIN', NULL, ARRAY['직무 관련 역량 개발'], NULL, 0, 'SYSTEM', true, false, NOW(), NOW());

-- 2. 중분류 (MIDDLE) INSERT
-- 외국어 공부 하위
INSERT INTO interest_embeddings (name, level, parent_id, full_path, embedding, usage_count, created_source, is_activated, deleted, created_at, updated_at)
SELECT
    v.name,
    'MIDDLE',
    m.id::text,
    ARRAY[m.name, v.name],
    NULL, 0, 'SYSTEM', true, false, NOW(), NOW()
FROM interest_embeddings m
CROSS JOIN (VALUES
    ('영어'),
    ('일본어'),
    ('중국어'),
    ('기타')
) v(name)
WHERE m.name = '외국어 공부' AND m.level = 'MAIN';

-- 재테크 및 투자 하위
INSERT INTO interest_embeddings (name, level, parent_id, full_path, embedding, usage_count, created_source, is_activated, deleted, created_at, updated_at)
SELECT
    v.name,
    'MIDDLE',
    m.id::text,
    ARRAY[m.name, v.name],
    NULL, 0, 'SYSTEM', true, false, NOW(), NOW()
FROM interest_embeddings m
CROSS JOIN (VALUES
    ('지출 관리 및 예산 세우기'),
    ('저축하기'),
    ('금융지식 쌓기'),
    ('투자 시작하기')
) v(name)
WHERE m.name = '재테크 및 투자' AND m.level = 'MAIN';

-- 체력관리 및 운동 하위
INSERT INTO interest_embeddings (name, level, parent_id, full_path, embedding, usage_count, created_source, is_activated, deleted, created_at, updated_at)
SELECT
    v.name,
    'MIDDLE',
    m.id::text,
    ARRAY[m.name, v.name],
    NULL, 0, 'SYSTEM', true, false, NOW(), NOW()
FROM interest_embeddings m
CROSS JOIN (VALUES
    ('헬스'),
    ('러닝'),
    ('필라테스/요가'),
    ('자전거')
) v(name)
WHERE m.name = '체력관리 및 운동' AND m.level = 'MAIN';

-- 자격증 공부 하위
INSERT INTO interest_embeddings (name, level, parent_id, full_path, embedding, usage_count, created_source, is_activated, deleted, created_at, updated_at)
SELECT
    v.name,
    'MIDDLE',
    m.id::text,
    ARRAY[m.name, v.name],
    NULL, 0, 'SYSTEM', true, false, NOW(), NOW()
FROM interest_embeddings m
CROSS JOIN (VALUES
    ('국가자격'),
    ('어학 능력'),
    ('기술 분야')
) v(name)
WHERE m.name = '자격증 공부' AND m.level = 'MAIN';

-- 직무 관련 역량 개발 하위
INSERT INTO interest_embeddings (name, level, parent_id, full_path, embedding, usage_count, created_source, is_activated, deleted, created_at, updated_at)
SELECT
    v.name,
    'MIDDLE',
    m.id::text,
    ARRAY[m.name, v.name],
    NULL, 0, 'SYSTEM', true, false, NOW(), NOW()
FROM interest_embeddings m
CROSS JOIN (VALUES
    ('AI 사용역량 강화'),
    ('이직 준비'),
    ('리더십'),
    ('업무 능력 향상')
) v(name)
WHERE m.name = '직무 관련 역량 개발' AND m.level = 'MAIN';

-- 3. 소분류 (SUB) INSERT
-- 영어 하위
INSERT INTO interest_embeddings (name, level, parent_id, full_path, embedding, usage_count, created_source, is_activated, deleted, created_at, updated_at)
SELECT
    v.name,
    'SUB',
    mid.id::text,
    ARRAY[(SELECT name FROM interest_embeddings WHERE id = mid.parent_id::bigint), mid.name, v.name],
    NULL, 0, 'SYSTEM', true, false, NOW(), NOW()
FROM interest_embeddings mid
CROSS JOIN (VALUES
    ('단어 학습'),
    ('시험 공부'),
    ('회화 공부'),
    ('직접 입력할게요')
) v(name)
WHERE mid.name = '영어' AND mid.level = 'MIDDLE';

-- 일본어 하위
INSERT INTO interest_embeddings (name, level, parent_id, full_path, embedding, usage_count, created_source, is_activated, deleted, created_at, updated_at)
SELECT
    v.name,
    'SUB',
    mid.id::text,
    ARRAY[(SELECT name FROM interest_embeddings WHERE id = mid.parent_id::bigint), mid.name, v.name],
    NULL, 0, 'SYSTEM', true, false, NOW(), NOW()
FROM interest_embeddings mid
CROSS JOIN (VALUES
    ('단어 학습'),
    ('시험 공부'),
    ('회화 공부'),
    ('직접 입력할게요')
) v(name)
WHERE mid.name = '일본어' AND mid.level = 'MIDDLE';

-- 중국어 하위
INSERT INTO interest_embeddings (name, level, parent_id, full_path, embedding, usage_count, created_source, is_activated, deleted, created_at, updated_at)
SELECT
    v.name,
    'SUB',
    mid.id::text,
    ARRAY[(SELECT name FROM interest_embeddings WHERE id = mid.parent_id::bigint), mid.name, v.name],
    NULL, 0, 'SYSTEM', true, false, NOW(), NOW()
FROM interest_embeddings mid
CROSS JOIN (VALUES
    ('단어 학습'),
    ('시험 공부'),
    ('회화 공부'),
    ('직접 입력할게요')
) v(name)
WHERE mid.name = '중국어' AND mid.level = 'MIDDLE';

-- 기타 하위 (외국어 공부)
INSERT INTO interest_embeddings (name, level, parent_id, full_path, embedding, usage_count, created_source, is_activated, deleted, created_at, updated_at)
SELECT
    v.name,
    'SUB',
    mid.id::text,
    ARRAY[(SELECT name FROM interest_embeddings WHERE id = mid.parent_id::bigint), mid.name, v.name],
    NULL, 0, 'SYSTEM', true, false, NOW(), NOW()
FROM interest_embeddings mid
CROSS JOIN (VALUES
    ('단어 학습'),
    ('시험 공부'),
    ('회화 공부'),
    ('직접 입력할게요')
) v(name)
WHERE mid.name = '기타' AND mid.level = 'MIDDLE'
  AND mid.parent_id IN (SELECT id::text FROM interest_embeddings WHERE name = '외국어 공부' AND level = 'MAIN');

-- 지출 관리 및 예산 세우기 하위
INSERT INTO interest_embeddings (name, level, parent_id, full_path, embedding, usage_count, created_source, is_activated, deleted, created_at, updated_at)
SELECT
    v.name,
    'SUB',
    mid.id::text,
    ARRAY[(SELECT name FROM interest_embeddings WHERE id = mid.parent_id::bigint), mid.name, v.name],
    NULL, 0, 'SYSTEM', true, false, NOW(), NOW()
FROM interest_embeddings mid
CROSS JOIN (VALUES
    ('지출 점검하기'),
    ('나의 소비 패턴 분석하기'),
    ('한달 예산 세우기'),
    ('카드 소비 패턴 파악하기'),
    ('직접 입력할게요')
) v(name)
WHERE mid.name = '지출 관리 및 예산 세우기' AND mid.level = 'MIDDLE';

-- 저축하기 하위
INSERT INTO interest_embeddings (name, level, parent_id, full_path, embedding, usage_count, created_source, is_activated, deleted, created_at, updated_at)
SELECT
    v.name,
    'SUB',
    mid.id::text,
    ARRAY[(SELECT name FROM interest_embeddings WHERE id = mid.parent_id::bigint), mid.name, v.name],
    NULL, 0, 'SYSTEM', true, false, NOW(), NOW()
FROM interest_embeddings mid
CROSS JOIN (VALUES
    ('단기·장기 저축 계획 세우기'),
    ('나만의 저축 루틴 만들기'),
    ('적금 상품 비교 방법 알아보기'),
    ('일상에서의 소비 절약 루틴 만들기'),
    ('직접 입력할게요')
) v(name)
WHERE mid.name = '저축하기' AND mid.level = 'MIDDLE';

-- 금융지식 쌓기 하위
INSERT INTO interest_embeddings (name, level, parent_id, full_path, embedding, usage_count, created_source, is_activated, deleted, created_at, updated_at)
SELECT
    v.name,
    'SUB',
    mid.id::text,
    ARRAY[(SELECT name FROM interest_embeddings WHERE id = mid.parent_id::bigint), mid.name, v.name],
    NULL, 0, 'SYSTEM', true, false, NOW(), NOW()
FROM interest_embeddings mid
CROSS JOIN (VALUES
    ('금융 상품 종류 익히기'),
    ('시장·경제 흐름 읽기'),
    ('금융 용어 기본 개념 이해하기'),
    ('직접 입력할게요')
) v(name)
WHERE mid.name = '금융지식 쌓기' AND mid.level = 'MIDDLE';

-- 투자 시작하기 하위
INSERT INTO interest_embeddings (name, level, parent_id, full_path, embedding, usage_count, created_source, is_activated, deleted, created_at, updated_at)
SELECT
    v.name,
    'SUB',
    mid.id::text,
    ARRAY[(SELECT name FROM interest_embeddings WHERE id = mid.parent_id::bigint), mid.name, v.name],
    NULL, 0, 'SYSTEM', true, false, NOW(), NOW()
FROM interest_embeddings mid
CROSS JOIN (VALUES
    ('소액 투자 시작하기'),
    ('나만의 투자 목표 설정하기'),
    ('리스크 이해하기'),
    ('현재 세계 주식 흐름 공부하기'),
    ('직접 입력할게요')
) v(name)
WHERE mid.name = '투자 시작하기' AND mid.level = 'MIDDLE';

-- 헬스 하위
INSERT INTO interest_embeddings (name, level, parent_id, full_path, embedding, usage_count, created_source, is_activated, deleted, created_at, updated_at)
SELECT
    v.name,
    'SUB',
    mid.id::text,
    ARRAY[(SELECT name FROM interest_embeddings WHERE id = mid.parent_id::bigint), mid.name, v.name],
    NULL, 0, 'SYSTEM', true, false, NOW(), NOW()
FROM interest_embeddings mid
CROSS JOIN (VALUES
    ('체력 증진'),
    ('체중 조절'),
    ('근력 향상'),
    ('체형 교정'),
    ('직접 입력할게요')
) v(name)
WHERE mid.name = '헬스' AND mid.level = 'MIDDLE';

-- 러닝 하위
INSERT INTO interest_embeddings (name, level, parent_id, full_path, embedding, usage_count, created_source, is_activated, deleted, created_at, updated_at)
SELECT
    v.name,
    'SUB',
    mid.id::text,
    ARRAY[(SELECT name FROM interest_embeddings WHERE id = mid.parent_id::bigint), mid.name, v.name],
    NULL, 0, 'SYSTEM', true, false, NOW(), NOW()
FROM interest_embeddings mid
CROSS JOIN (VALUES
    ('체력 증진'),
    ('체중 조절'),
    ('스트레스 해소'),
    ('마라톤/러닝대회 준비'),
    ('직접 입력할게요')
) v(name)
WHERE mid.name = '러닝' AND mid.level = 'MIDDLE';

-- 필라테스/요가 하위
INSERT INTO interest_embeddings (name, level, parent_id, full_path, embedding, usage_count, created_source, is_activated, deleted, created_at, updated_at)
SELECT
    v.name,
    'SUB',
    mid.id::text,
    ARRAY[(SELECT name FROM interest_embeddings WHERE id = mid.parent_id::bigint), mid.name, v.name],
    NULL, 0, 'SYSTEM', true, false, NOW(), NOW()
FROM interest_embeddings mid
CROSS JOIN (VALUES
    ('체력 증진'),
    ('체중 조절'),
    ('근력 향상'),
    ('체형 교정'),
    ('직접 입력할게요')
) v(name)
WHERE mid.name = '필라테스/요가' AND mid.level = 'MIDDLE';

-- 자전거 하위
INSERT INTO interest_embeddings (name, level, parent_id, full_path, embedding, usage_count, created_source, is_activated, deleted, created_at, updated_at)
SELECT
    v.name,
    'SUB',
    mid.id::text,
    ARRAY[(SELECT name FROM interest_embeddings WHERE id = mid.parent_id::bigint), mid.name, v.name],
    NULL, 0, 'SYSTEM', true, false, NOW(), NOW()
FROM interest_embeddings mid
CROSS JOIN (VALUES
    ('체력 증진'),
    ('체중 조절'),
    ('근력 향상'),
    ('심폐 지구력 향상'),
    ('직접 입력할게요')
) v(name)
WHERE mid.name = '자전거' AND mid.level = 'MIDDLE';

-- 국가자격 하위
INSERT INTO interest_embeddings (name, level, parent_id, full_path, embedding, usage_count, created_source, is_activated, deleted, created_at, updated_at)
SELECT
    v.name,
    'SUB',
    mid.id::text,
    ARRAY[(SELECT name FROM interest_embeddings WHERE id = mid.parent_id::bigint), mid.name, v.name],
    NULL, 0, 'SYSTEM', true, false, NOW(), NOW()
FROM interest_embeddings mid
CROSS JOIN (VALUES
    ('정보처리기사'),
    ('공인중개사'),
    ('컴퓨터활용능력 2급'),
    ('SQLD'),
    ('직접 입력할게요')
) v(name)
WHERE mid.name = '국가자격' AND mid.level = 'MIDDLE';

-- 어학 능력 하위
INSERT INTO interest_embeddings (name, level, parent_id, full_path, embedding, usage_count, created_source, is_activated, deleted, created_at, updated_at)
SELECT
    v.name,
    'SUB',
    mid.id::text,
    ARRAY[(SELECT name FROM interest_embeddings WHERE id = mid.parent_id::bigint), mid.name, v.name],
    NULL, 0, 'SYSTEM', true, false, NOW(), NOW()
FROM interest_embeddings mid
CROSS JOIN (VALUES
    ('토익'),
    ('오픽'),
    ('JLPT'),
    ('HSK'),
    ('직접 입력할게요')
) v(name)
WHERE mid.name = '어학 능력' AND mid.level = 'MIDDLE';

-- 기술 분야 하위
INSERT INTO interest_embeddings (name, level, parent_id, full_path, embedding, usage_count, created_source, is_activated, deleted, created_at, updated_at)
SELECT
    v.name,
    'SUB',
    mid.id::text,
    ARRAY[(SELECT name FROM interest_embeddings WHERE id = mid.parent_id::bigint), mid.name, v.name],
    NULL, 0, 'SYSTEM', true, false, NOW(), NOW()
FROM interest_embeddings mid
CROSS JOIN (VALUES
    ('직업상담사'),
    ('청소년지도사'),
    ('반려동물 장례지도사'),
    ('바리스타'),
    ('직접 입력할게요')
) v(name)
WHERE mid.name = '기술 분야' AND mid.level = 'MIDDLE';

-- AI 사용역량 강화 하위
INSERT INTO interest_embeddings (name, level, parent_id, full_path, embedding, usage_count, created_source, is_activated, deleted, created_at, updated_at)
SELECT
    v.name,
    'SUB',
    mid.id::text,
    ARRAY[(SELECT name FROM interest_embeddings WHERE id = mid.parent_id::bigint), mid.name, v.name],
    NULL, 0, 'SYSTEM', true, false, NOW(), NOW()
FROM interest_embeddings mid
CROSS JOIN (VALUES
    ('AI 프롬프트 학습'),
    ('실무 AI 프로그램 탐색 및 활용'),
    ('직접 입력할게요')
) v(name)
WHERE mid.name = 'AI 사용역량 강화' AND mid.level = 'MIDDLE';

-- 이직 준비 하위 (직무 관련 역량 개발)
INSERT INTO interest_embeddings (name, level, parent_id, full_path, embedding, usage_count, created_source, is_activated, deleted, created_at, updated_at)
SELECT
    v.name,
    'SUB',
    mid.id::text,
    ARRAY[(SELECT name FROM interest_embeddings WHERE id = mid.parent_id::bigint), mid.name, v.name],
    NULL, 0, 'SYSTEM', true, false, NOW(), NOW()
FROM interest_embeddings mid
CROSS JOIN (VALUES
    ('포트폴리오 제작'),
    ('이력서·경력기술서 관리'),
    ('면접 대비'),
    ('직접 입력할게요')
) v(name)
WHERE mid.name = '이직 준비' AND mid.level = 'MIDDLE'
  AND mid.parent_id IN (SELECT id::text FROM interest_embeddings WHERE name = '직무 관련 역량 개발' AND level = 'MAIN');

-- 리더십 하위
INSERT INTO interest_embeddings (name, level, parent_id, full_path, embedding, usage_count, created_source, is_activated, deleted, created_at, updated_at)
SELECT
    v.name,
    'SUB',
    mid.id::text,
    ARRAY[(SELECT name FROM interest_embeddings WHERE id = mid.parent_id::bigint), mid.name, v.name],
    NULL, 0, 'SYSTEM', true, false, NOW(), NOW()
FROM interest_embeddings mid
CROSS JOIN (VALUES
    ('성과관리 능력 향상'),
    ('조직관리 능력 향상'),
    ('직접 입력할게요')
) v(name)
WHERE mid.name = '리더십' AND mid.level = 'MIDDLE';

-- 업무 능력 향상 하위
INSERT INTO interest_embeddings (name, level, parent_id, full_path, embedding, usage_count, created_source, is_activated, deleted, created_at, updated_at)
SELECT
    v.name,
    'SUB',
    mid.id::text,
    ARRAY[(SELECT name FROM interest_embeddings WHERE id = mid.parent_id::bigint), mid.name, v.name],
    NULL, 0, 'SYSTEM', true, false, NOW(), NOW()
FROM interest_embeddings mid
CROSS JOIN (VALUES
    ('업무 효율성 향상'),
    ('커뮤니케이션·협업 능력 향상'),
    ('문제 해결 능력 향상'),
    ('문서 정리 능력 향상'),
    ('직접 입력할게요')
) v(name)
WHERE mid.name = '업무 능력 향상' AND mid.level = 'MIDDLE';

-- 확인용 쿼리
SELECT
    level,
    name,
    full_path,
    parent_id,
    usage_count,
    created_source,
    is_activated
FROM interest_embeddings
ORDER BY
    CASE level WHEN 'MAIN' THEN 1 WHEN 'MIDDLE' THEN 2 WHEN 'SUB' THEN 3 END,
    full_path;
