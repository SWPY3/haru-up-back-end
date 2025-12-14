-- interest_embeddings 테이블에 초기 데이터 INSERT
-- 실행 방법: cat insert_interest.sql | docker exec -i -e PGPASSWORD={PASSWORD} postgres psql -U haruup_user -d haruup

-- 기존 데이터 삭제 (필요시)
-- TRUNCATE interest_embeddings RESTART IDENTITY CASCADE;

-- 1. 대분류 (MAIN) INSERT
INSERT INTO interest_embeddings (name, level, parent_id, full_path, embedding, usage_count, created_source, is_activated, created_at, updated_at)
VALUES
    ('외국어 공부', 'MAIN', NULL, ARRAY['외국어 공부'], NULL, 0, 'SYSTEM', true, NOW(), NOW()),
    ('재테크/투자 공부', 'MAIN', NULL, ARRAY['재테크/투자 공부'], NULL, 0, 'SYSTEM', true, NOW(), NOW()),
    ('체력관리 및 운동', 'MAIN', NULL, ARRAY['체력관리 및 운동'], NULL, 0, 'SYSTEM', true, NOW(), NOW()),
    ('자격증 공부', 'MAIN', NULL, ARRAY['자격증 공부'], NULL, 0, 'SYSTEM', true, NOW(), NOW()),
    ('직무 관련 역량 개발', 'MAIN', NULL, ARRAY['직무 관련 역량 개발'], NULL, 0, 'SYSTEM', true, NOW(), NOW());

-- 2. 중분류 (MIDDLE) INSERT
-- 외국어 공부 하위
INSERT INTO interest_embeddings (name, level, parent_id, full_path, embedding, usage_count, created_source, is_activated, created_at, updated_at)
SELECT
    v.name,
    'MIDDLE',
    m.id::text,
    ARRAY[m.name, v.name],
    NULL, 0, 'SYSTEM', true, NOW(), NOW()
FROM interest_embeddings m
CROSS JOIN (VALUES
    ('영어'),
    ('일본어'),
    ('중국어'),
    ('기타 외국어')
) v(name)
WHERE m.name = '외국어 공부' AND m.level = 'MAIN';

-- 재테크/투자 공부 하위
INSERT INTO interest_embeddings (name, level, parent_id, full_path, embedding, usage_count, created_source, is_activated, created_at, updated_at)
SELECT
    v.name,
    'MIDDLE',
    m.id::text,
    ARRAY[m.name, v.name],
    NULL, 0, 'SYSTEM', true, NOW(), NOW()
FROM interest_embeddings m
CROSS JOIN (VALUES
    ('지출 관리·예산 세우기'),
    ('저축·적금하기'),
    ('금융지식 쌓기'),
    ('투자 시작하기')
) v(name)
WHERE m.name = '재테크/투자 공부' AND m.level = 'MAIN';

-- 체력관리 및 운동 하위
INSERT INTO interest_embeddings (name, level, parent_id, full_path, embedding, usage_count, created_source, is_activated, created_at, updated_at)
SELECT
    v.name,
    'MIDDLE',
    m.id::text,
    ARRAY[m.name, v.name],
    NULL, 0, 'SYSTEM', true, NOW(), NOW()
FROM interest_embeddings m
CROSS JOIN (VALUES
    ('헬스'),
    ('러닝'),
    ('필라테스/요가'),
    ('자전거')
) v(name)
WHERE m.name = '체력관리 및 운동' AND m.level = 'MAIN';

-- 자격증 공부 하위
INSERT INTO interest_embeddings (name, level, parent_id, full_path, embedding, usage_count, created_source, is_activated, created_at, updated_at)
SELECT
    v.name,
    'MIDDLE',
    m.id::text,
    ARRAY[m.name, v.name],
    NULL, 0, 'SYSTEM', true, NOW(), NOW()
FROM interest_embeddings m
CROSS JOIN (VALUES
    ('직무 전문 분야'),
    ('국가자격'),
    ('어학 자격 능력'),
    ('기술 분야')
) v(name)
WHERE m.name = '자격증 공부' AND m.level = 'MAIN';

-- 직무 관련 역량 개발 하위
INSERT INTO interest_embeddings (name, level, parent_id, full_path, embedding, usage_count, created_source, is_activated, created_at, updated_at)
SELECT
    v.name,
    'MIDDLE',
    m.id::text,
    ARRAY[m.name, v.name],
    NULL, 0, 'SYSTEM', true, NOW(), NOW()
FROM interest_embeddings m
CROSS JOIN (VALUES
    ('직무별 프로그램 학습'),
    ('커리어 전환 준비'),
    ('자격증'),
    ('업무 능력 향상')
) v(name)
WHERE m.name = '직무 관련 역량 개발' AND m.level = 'MAIN';

-- 3. 소분류 (SUB) INSERT
-- 영어 하위
INSERT INTO interest_embeddings (name, level, parent_id, full_path, embedding, usage_count, created_source, is_activated, created_at, updated_at)
SELECT
    v.name,
    'SUB',
    mid.id::text,
    ARRAY[(SELECT name FROM interest_embeddings WHERE id = mid.parent_id::bigint), mid.name, v.name],
    NULL, 0, 'SYSTEM', true, NOW(), NOW()
FROM interest_embeddings mid
CROSS JOIN (VALUES
    ('단어 학습'),
    ('시험 공부 (토익/토익스피킹/오픽/토플 등)'),
    ('회화 공부'),
    ('직접입력')
) v(name)
WHERE mid.name = '영어' AND mid.level = 'MIDDLE';

-- 일본어 하위
INSERT INTO interest_embeddings (name, level, parent_id, full_path, embedding, usage_count, created_source, is_activated, created_at, updated_at)
SELECT
    v.name,
    'SUB',
    mid.id::text,
    ARRAY[(SELECT name FROM interest_embeddings WHERE id = mid.parent_id::bigint), mid.name, v.name],
    NULL, 0, 'SYSTEM', true, NOW(), NOW()
FROM interest_embeddings mid
CROSS JOIN (VALUES
    ('단어 학습'),
    ('시험 공부'),
    ('회화 공부'),
    ('직접입력')
) v(name)
WHERE mid.name = '일본어' AND mid.level = 'MIDDLE';

-- 중국어 하위
INSERT INTO interest_embeddings (name, level, parent_id, full_path, embedding, usage_count, created_source, is_activated, created_at, updated_at)
SELECT
    v.name,
    'SUB',
    mid.id::text,
    ARRAY[(SELECT name FROM interest_embeddings WHERE id = mid.parent_id::bigint), mid.name, v.name],
    NULL, 0, 'SYSTEM', true, NOW(), NOW()
FROM interest_embeddings mid
CROSS JOIN (VALUES
    ('단어 학습'),
    ('시험 공부'),
    ('회화 공부'),
    ('직접입력')
) v(name)
WHERE mid.name = '중국어' AND mid.level = 'MIDDLE';

-- 기타 외국어 하위
INSERT INTO interest_embeddings (name, level, parent_id, full_path, embedding, usage_count, created_source, is_activated, created_at, updated_at)
SELECT
    v.name,
    'SUB',
    mid.id::text,
    ARRAY[(SELECT name FROM interest_embeddings WHERE id = mid.parent_id::bigint), mid.name, v.name],
    NULL, 0, 'SYSTEM', true, NOW(), NOW()
FROM interest_embeddings mid
CROSS JOIN (VALUES
    ('단어 학습'),
    ('시험 공부'),
    ('회화 공부'),
    ('직접입력')
) v(name)
WHERE mid.name = '기타 외국어' AND mid.level = 'MIDDLE';

-- 지출 관리·예산 세우기 하위
INSERT INTO interest_embeddings (name, level, parent_id, full_path, embedding, usage_count, created_source, is_activated, created_at, updated_at)
SELECT
    v.name,
    'SUB',
    mid.id::text,
    ARRAY[(SELECT name FROM interest_embeddings WHERE id = mid.parent_id::bigint), mid.name, v.name],
    NULL, 0, 'SYSTEM', true, NOW(), NOW()
FROM interest_embeddings mid
CROSS JOIN (VALUES
    ('지출 점검하기'),
    ('카테고리별 소비 분석'),
    ('고정지출/변동지출 정리'),
    ('한달 예산 세우기'),
    ('카드 소비 패턴 파악'),
    ('직접입력')
) v(name)
WHERE mid.name = '지출 관리·예산 세우기' AND mid.level = 'MIDDLE';

-- 저축·적금하기 하위
INSERT INTO interest_embeddings (name, level, parent_id, full_path, embedding, usage_count, created_source, is_activated, created_at, updated_at)
SELECT
    v.name,
    'SUB',
    mid.id::text,
    ARRAY[(SELECT name FROM interest_embeddings WHERE id = mid.parent_id::bigint), mid.name, v.name],
    NULL, 0, 'SYSTEM', true, NOW(), NOW()
FROM interest_embeddings mid
CROSS JOIN (VALUES
    ('월간 저축 목표 설정'),
    ('저축 루틴 만들기'),
    ('적금 시작하기'),
    ('소비 절약 루틴 만들기'),
    ('직접입력')
) v(name)
WHERE mid.name = '저축·적금하기' AND mid.level = 'MIDDLE';

-- 금융지식 쌓기 하위
INSERT INTO interest_embeddings (name, level, parent_id, full_path, embedding, usage_count, created_source, is_activated, created_at, updated_at)
SELECT
    v.name,
    'SUB',
    mid.id::text,
    ARRAY[(SELECT name FROM interest_embeddings WHERE id = mid.parent_id::bigint), mid.name, v.name],
    NULL, 0, 'SYSTEM', true, NOW(), NOW()
FROM interest_embeddings mid
CROSS JOIN (VALUES
    ('금융 상품 종류 익히기(주식/ETF)'),
    ('시장·경제 흐름 읽기 (환율/물가)'),
    ('금융 용어 기본 개념 배우기 (금리/세금)'),
    ('직접입력')
) v(name)
WHERE mid.name = '금융지식 쌓기' AND mid.level = 'MIDDLE';

-- 투자 시작하기 하위
INSERT INTO interest_embeddings (name, level, parent_id, full_path, embedding, usage_count, created_source, is_activated, created_at, updated_at)
SELECT
    v.name,
    'SUB',
    mid.id::text,
    ARRAY[(SELECT name FROM interest_embeddings WHERE id = mid.parent_id::bigint), mid.name, v.name],
    NULL, 0, 'SYSTEM', true, NOW(), NOW()
FROM interest_embeddings mid
CROSS JOIN (VALUES
    ('소액 투자'),
    ('투자 상품 이해하기'),
    ('투자금 만들기'),
    ('나에게 맞는 투자 방향 찾기'),
    ('직접입력')
) v(name)
WHERE mid.name = '투자 시작하기' AND mid.level = 'MIDDLE';

-- 헬스 하위
INSERT INTO interest_embeddings (name, level, parent_id, full_path, embedding, usage_count, created_source, is_activated, created_at, updated_at)
SELECT
    v.name,
    'SUB',
    mid.id::text,
    ARRAY[(SELECT name FROM interest_embeddings WHERE id = mid.parent_id::bigint), mid.name, v.name],
    NULL, 0, 'SYSTEM', true, NOW(), NOW()
FROM interest_embeddings mid
CROSS JOIN (VALUES
    ('체력 증진'),
    ('체중 조절'),
    ('근력 키우기'),
    ('체형 교정'),
    ('직접입력')
) v(name)
WHERE mid.name = '헬스' AND mid.level = 'MIDDLE';

-- 러닝 하위
INSERT INTO interest_embeddings (name, level, parent_id, full_path, embedding, usage_count, created_source, is_activated, created_at, updated_at)
SELECT
    v.name,
    'SUB',
    mid.id::text,
    ARRAY[(SELECT name FROM interest_embeddings WHERE id = mid.parent_id::bigint), mid.name, v.name],
    NULL, 0, 'SYSTEM', true, NOW(), NOW()
FROM interest_embeddings mid
CROSS JOIN (VALUES
    ('체력 증진'),
    ('체중 조절'),
    ('스트레스 해소'),
    ('마라톤/러닝대회 준비'),
    ('직접입력')
) v(name)
WHERE mid.name = '러닝' AND mid.level = 'MIDDLE';

-- 필라테스/요가 하위
INSERT INTO interest_embeddings (name, level, parent_id, full_path, embedding, usage_count, created_source, is_activated, created_at, updated_at)
SELECT
    v.name,
    'SUB',
    mid.id::text,
    ARRAY[(SELECT name FROM interest_embeddings WHERE id = mid.parent_id::bigint), mid.name, v.name],
    NULL, 0, 'SYSTEM', true, NOW(), NOW()
FROM interest_embeddings mid
CROSS JOIN (VALUES
    ('체력 증진'),
    ('체중 조절'),
    ('근력 키우기'),
    ('체형 교정'),
    ('직접입력')
) v(name)
WHERE mid.name = '필라테스/요가' AND mid.level = 'MIDDLE';

-- 자전거 하위
INSERT INTO interest_embeddings (name, level, parent_id, full_path, embedding, usage_count, created_source, is_activated, created_at, updated_at)
SELECT
    v.name,
    'SUB',
    mid.id::text,
    ARRAY[(SELECT name FROM interest_embeddings WHERE id = mid.parent_id::bigint), mid.name, v.name],
    NULL, 0, 'SYSTEM', true, NOW(), NOW()
FROM interest_embeddings mid
CROSS JOIN (VALUES
    ('체력 증진'),
    ('체중 조절'),
    ('근력 키우기'),
    ('출퇴근·이동 등 교통수단 활용'),
    ('직접입력')
) v(name)
WHERE mid.name = '자전거' AND mid.level = 'MIDDLE';

-- 직무 전문 분야 하위
INSERT INTO interest_embeddings (name, level, parent_id, full_path, embedding, usage_count, created_source, is_activated, created_at, updated_at)
SELECT
    v.name,
    'SUB',
    mid.id::text,
    ARRAY[(SELECT name FROM interest_embeddings WHERE id = mid.parent_id::bigint), mid.name, v.name],
    NULL, 0, 'SYSTEM', true, NOW(), NOW()
FROM interest_embeddings mid
CROSS JOIN (VALUES
    ('(온보딩 시 입력한 개인정보 기반)'),
    ('직접입력')
) v(name)
WHERE mid.name = '직무 전문 분야' AND mid.level = 'MIDDLE';

-- 국가자격 하위
INSERT INTO interest_embeddings (name, level, parent_id, full_path, embedding, usage_count, created_source, is_activated, created_at, updated_at)
SELECT
    v.name,
    'SUB',
    mid.id::text,
    ARRAY[(SELECT name FROM interest_embeddings WHERE id = mid.parent_id::bigint), mid.name, v.name],
    NULL, 0, 'SYSTEM', true, NOW(), NOW()
FROM interest_embeddings mid
CROSS JOIN (VALUES
    ('전기기능사'),
    ('소방설비기사'),
    ('공인회계사'),
    ('직접입력')
) v(name)
WHERE mid.name = '국가자격' AND mid.level = 'MIDDLE';

-- 어학 자격 능력 하위
INSERT INTO interest_embeddings (name, level, parent_id, full_path, embedding, usage_count, created_source, is_activated, created_at, updated_at)
SELECT
    v.name,
    'SUB',
    mid.id::text,
    ARRAY[(SELECT name FROM interest_embeddings WHERE id = mid.parent_id::bigint), mid.name, v.name],
    NULL, 0, 'SYSTEM', true, NOW(), NOW()
FROM interest_embeddings mid
CROSS JOIN (VALUES
    ('토익'),
    ('토익스피킹'),
    ('오픽'),
    ('토플'),
    ('HSK'),
    ('직접입력')
) v(name)
WHERE mid.name = '어학 자격 능력' AND mid.level = 'MIDDLE';

-- 기술 분야 하위
INSERT INTO interest_embeddings (name, level, parent_id, full_path, embedding, usage_count, created_source, is_activated, created_at, updated_at)
SELECT
    v.name,
    'SUB',
    mid.id::text,
    ARRAY[(SELECT name FROM interest_embeddings WHERE id = mid.parent_id::bigint), mid.name, v.name],
    NULL, 0, 'SYSTEM', true, NOW(), NOW()
FROM interest_embeddings mid
CROSS JOIN (VALUES
    ('손뜨개전문가'),
    ('청소 전문가'),
    ('반려동물장례지도사'),
    ('베이비시터'),
    ('전기 기사'),
    ('컬러리스트 기사'),
    ('미용사(네일)'),
    ('직접입력')
) v(name)
WHERE mid.name = '기술 분야' AND mid.level = 'MIDDLE';

-- 직무별 프로그램 학습 하위
INSERT INTO interest_embeddings (name, level, parent_id, full_path, embedding, usage_count, created_source, is_activated, created_at, updated_at)
SELECT
    v.name,
    'SUB',
    mid.id::text,
    ARRAY[(SELECT name FROM interest_embeddings WHERE id = mid.parent_id::bigint), mid.name, v.name],
    NULL, 0, 'SYSTEM', true, NOW(), NOW()
FROM interest_embeddings mid
CROSS JOIN (VALUES
    ('AI 프롬프트 학습'),
    ('작업용 프로그램 학습'),
    ('직접입력')
) v(name)
WHERE mid.name = '직무별 프로그램 학습' AND mid.level = 'MIDDLE';

-- 커리어 전환 준비 하위
INSERT INTO interest_embeddings (name, level, parent_id, full_path, embedding, usage_count, created_source, is_activated, created_at, updated_at)
SELECT
    v.name,
    'SUB',
    mid.id::text,
    ARRAY[(SELECT name FROM interest_embeddings WHERE id = mid.parent_id::bigint), mid.name, v.name],
    NULL, 0, 'SYSTEM', true, NOW(), NOW()
FROM interest_embeddings mid
CROSS JOIN (VALUES
    ('포트폴리오 제작'),
    ('이력서 관리'),
    ('자격증 취득'),
    ('어학 공부'),
    ('직접입력')
) v(name)
WHERE mid.name = '커리어 전환 준비' AND mid.level = 'MIDDLE';

-- 자격증 (직무 관련 역량 개발) 하위
INSERT INTO interest_embeddings (name, level, parent_id, full_path, embedding, usage_count, created_source, is_activated, created_at, updated_at)
SELECT
    v.name,
    'SUB',
    mid.id::text,
    ARRAY[(SELECT name FROM interest_embeddings WHERE id = mid.parent_id::bigint), mid.name, v.name],
    NULL, 0, 'SYSTEM', true, NOW(), NOW()
FROM interest_embeddings mid
CROSS JOIN (VALUES
    ('직무 전문 분야 자격증'),
    ('어학'),
    ('직접입력')
) v(name)
WHERE mid.name = '자격증' AND mid.level = 'MIDDLE'
  AND mid.parent_id IN (SELECT id::text FROM interest_embeddings WHERE name = '직무 관련 역량 개발' AND level = 'MAIN');

-- 업무 능력 향상 하위
INSERT INTO interest_embeddings (name, level, parent_id, full_path, embedding, usage_count, created_source, is_activated, created_at, updated_at)
SELECT
    v.name,
    'SUB',
    mid.id::text,
    ARRAY[(SELECT name FROM interest_embeddings WHERE id = mid.parent_id::bigint), mid.name, v.name],
    NULL, 0, 'SYSTEM', true, NOW(), NOW()
FROM interest_embeddings mid
CROSS JOIN (VALUES
    ('업무 효율 높이기 (우선순위/시간관리)'),
    ('커뮤니케이션·협업 능력 키우기'),
    ('문제 해결능력 향상'),
    ('문서·기획·정리 스킬 향상(PPT·보고서)'),
    ('직접입력')
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
