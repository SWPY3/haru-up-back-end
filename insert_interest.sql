-- Interest 테이블에 초기 데이터 INSERT
-- 실행 방법: cat insert_interest.sql | docker exec -i -e PGPASSWORD={PASSWORD} postgres psql -U haruup_user -d haruup

-- 1. 대분류 (depth=1, parent_id=null) INSERT
WITH inserted_major AS (
    INSERT INTO interest (parent_id, depth, interest_name, created_source, deleted, created_at, updated_at)
    VALUES
        (NULL, 1, '외국어 공부', 'SYSTEM'::created_source_type, false, NOW(), NOW()),
        (NULL, 1, '재테크/투자 공부', 'SYSTEM'::created_source_type, false, NOW(), NOW()),
        (NULL, 1, '체력관리 및 운동', 'SYSTEM'::created_source_type, false, NOW(), NOW()),
        (NULL, 1, '자격증 공부', 'SYSTEM'::created_source_type, false, NOW(), NOW()),
        (NULL, 1, '직무 관련 역량 개발', 'SYSTEM'::created_source_type, false, NOW(), NOW())
    RETURNING id, interest_name
),

-- 2. 중분류 (depth=2) INSERT
inserted_middle AS (
    INSERT INTO interest (parent_id, depth, interest_name, created_source, deleted, created_at, updated_at)
    SELECT
        m.id,
        2,
        v.interest_name,
        'SYSTEM'::created_source_type,
        false,
        NOW(),
        NOW()
    FROM inserted_major m
    CROSS JOIN LATERAL (VALUES
        -- 외국어 공부
        ('영어'),
        ('일본어'),
        ('중국어'),
        ('기타')
    ) v(interest_name)
    WHERE m.interest_name = '외국어 공부'

    UNION ALL

    SELECT
        m.id,
        2,
        v.interest_name,
        'SYSTEM'::created_source_type,
        false,
        NOW(),
        NOW()
    FROM inserted_major m
    CROSS JOIN LATERAL (VALUES
        -- 재테크/투자 공부
        ('지출 관리·예산 세우기'),
        ('저축·적금하기'),
        ('금융지식 쌓기'),
        ('투자 시작하기')
    ) v(interest_name)
    WHERE m.interest_name = '재테크/투자 공부'

    UNION ALL

    SELECT
        m.id,
        2,
        v.interest_name,
        'SYSTEM'::created_source_type,
        false,
        NOW(),
        NOW()
    FROM inserted_major m
    CROSS JOIN LATERAL (VALUES
        -- 체력관리 및 운동
        ('헬스'),
        ('러닝'),
        ('필라테스/요가'),
        ('자전거')
    ) v(interest_name)
    WHERE m.interest_name = '체력관리 및 운동'

    UNION ALL

    SELECT
        m.id,
        2,
        v.interest_name,
        'SYSTEM'::created_source_type,
        false,
        NOW(),
        NOW()
    FROM inserted_major m
    CROSS JOIN LATERAL (VALUES
        -- 자격증 공부
        ('직무 전문 분야'),
        ('국가자격'),
        ('어학 자격 능력'),
        ('기술 분야')
    ) v(interest_name)
    WHERE m.interest_name = '자격증 공부'

    UNION ALL

    SELECT
        m.id,
        2,
        v.interest_name,
        'SYSTEM'::created_source_type,
        false,
        NOW(),
        NOW()
    FROM inserted_major m
    CROSS JOIN LATERAL (VALUES
        -- 직무 관련 역량 개발
        ('직무별 프로그램 학습'),
        ('커리어 전환 준비'),
        ('자격증'),
        ('업무 능력 향상')
    ) v(interest_name)
    WHERE m.interest_name = '직무 관련 역량 개발'

    RETURNING id, parent_id, interest_name
)

-- 3. 소분류 (depth=3) INSERT
INSERT INTO interest (parent_id, depth, interest_name, created_source, deleted, created_at, updated_at)
SELECT
    mid.id,
    3,
    v.interest_name,
    'SYSTEM'::created_source_type,
    false,
    NOW(),
    NOW()
FROM inserted_middle mid
CROSS JOIN LATERAL (VALUES
    -- 영어
    ('단어 학습'),
    ('시험 공부 (토익/토익스피킹/오픽/토플 등)'),
    ('회화 공부')
) v(interest_name)
WHERE mid.interest_name = '영어'

UNION ALL

SELECT
    mid.id,
    3,
    v.interest_name,
    'SYSTEM'::created_source_type,
    false,
    NOW(),
    NOW()
FROM inserted_middle mid
CROSS JOIN LATERAL (VALUES
    -- 일본어
    ('단어 학습'),
    ('시험 공부'),
    ('회화 공부')
) v(interest_name)
WHERE mid.interest_name = '일본어'

UNION ALL

SELECT
    mid.id,
    3,
    v.interest_name,
    'SYSTEM'::created_source_type,
    false,
    NOW(),
    NOW()
FROM inserted_middle mid
CROSS JOIN LATERAL (VALUES
    -- 중국어
    ('단어 학습'),
    ('시험 공부'),
    ('회화 공부')
) v(interest_name)
WHERE mid.interest_name = '중국어'

UNION ALL

SELECT
    mid.id,
    3,
    v.interest_name,
    'SYSTEM'::created_source_type,
    false,
    NOW(),
    NOW()
FROM inserted_middle mid
CROSS JOIN LATERAL (VALUES
    -- 기타 (외국어)
    ('단어 학습'),
    ('시험 공부'),
    ('회화 공부')
) v(interest_name)
WHERE mid.interest_name = '기타'

UNION ALL

SELECT
    mid.id,
    3,
    v.interest_name,
    'SYSTEM'::created_source_type,
    false,
    NOW(),
    NOW()
FROM inserted_middle mid
CROSS JOIN LATERAL (VALUES
    -- 지출 관리·예산 세우기
    ('지출 점검하기'),
    ('카테고리별 소비 분석'),
    ('고정지출/변동지출 정리'),
    ('한달 예산 세우기'),
    ('카드 소비 패턴 파악')
) v(interest_name)
WHERE mid.interest_name = '지출 관리·예산 세우기'

UNION ALL

SELECT
    mid.id,
    3,
    v.interest_name,
    'SYSTEM'::created_source_type,
    false,
    NOW(),
    NOW()
FROM inserted_middle mid
CROSS JOIN LATERAL (VALUES
    -- 저축·적금하기
    ('월간 저축 목표 설정'),
    ('저축 루틴 만들기'),
    ('적금 시작하기'),
    ('소비 절약 루틴 만들기')
) v(interest_name)
WHERE mid.interest_name = '저축·적금하기'

UNION ALL

SELECT
    mid.id,
    3,
    v.interest_name,
    'SYSTEM'::created_source_type,
    false,
    NOW(),
    NOW()
FROM inserted_middle mid
CROSS JOIN LATERAL (VALUES
    -- 금융지식 쌓기
    ('금융 상품 종류 익히기(주식/ETF)'),
    ('시장·경제 흐름 읽기 (환율/물가)'),
    ('금융 용어 기본 개념 배우기 (금리/세금)')
) v(interest_name)
WHERE mid.interest_name = '금융지식 쌓기'

UNION ALL

SELECT
    mid.id,
    3,
    v.interest_name,
    'SYSTEM'::created_source_type,
    false,
    NOW(),
    NOW()
FROM inserted_middle mid
CROSS JOIN LATERAL (VALUES
    -- 투자 시작하기
    ('소액 투자'),
    ('투자 상품 이해하기'),
    ('투자금 만들기'),
    ('나에게 맞는 투자 방향 찾기')
) v(interest_name)
WHERE mid.interest_name = '투자 시작하기'

UNION ALL

SELECT
    mid.id,
    3,
    v.interest_name,
    'SYSTEM'::created_source_type,
    false,
    NOW(),
    NOW()
FROM inserted_middle mid
CROSS JOIN LATERAL (VALUES
    -- 헬스
    ('체력 증진'),
    ('체중 조절'),
    ('근력 키우기'),
    ('체형 교정')
) v(interest_name)
WHERE mid.interest_name = '헬스'

UNION ALL

SELECT
    mid.id,
    3,
    v.interest_name,
    'SYSTEM'::created_source_type,
    false,
    NOW(),
    NOW()
FROM inserted_middle mid
CROSS JOIN LATERAL (VALUES
    -- 러닝
    ('체력 증진'),
    ('체중 조절'),
    ('스트레스 해소'),
    ('마라톤/러닝대회 준비')
) v(interest_name)
WHERE mid.interest_name = '러닝'

UNION ALL

SELECT
    mid.id,
    3,
    v.interest_name,
    'SYSTEM'::created_source_type,
    false,
    NOW(),
    NOW()
FROM inserted_middle mid
CROSS JOIN LATERAL (VALUES
    -- 필라테스/요가
    ('체력 증진'),
    ('체중 조절'),
    ('근력 키우기'),
    ('체형 교정')
) v(interest_name)
WHERE mid.interest_name = '필라테스/요가'

UNION ALL

SELECT
    mid.id,
    3,
    v.interest_name,
    'SYSTEM'::created_source_type,
    false,
    NOW(),
    NOW()
FROM inserted_middle mid
CROSS JOIN LATERAL (VALUES
    -- 자전거
    ('체력 증진'),
    ('체중 조절'),
    ('근력 키우기'),
    ('출퇴근·이동 등 교통수단 활용')
) v(interest_name)
WHERE mid.interest_name = '자전거'

UNION ALL

SELECT
    mid.id,
    3,
    v.interest_name,
    'SYSTEM'::created_source_type,
    false,
    NOW(),
    NOW()
FROM inserted_middle mid
CROSS JOIN LATERAL (VALUES
    -- 직무 전문 분야
    ('(온보딩 시 입력한 개인정보 기반)')
) v(interest_name)
WHERE mid.interest_name = '직무 전문 분야'

UNION ALL

SELECT
    mid.id,
    3,
    v.interest_name,
    'SYSTEM'::created_source_type,
    false,
    NOW(),
    NOW()
FROM inserted_middle mid
CROSS JOIN LATERAL (VALUES
    -- 국가자격
    ('전기기능사'),
    ('소방설비기사'),
    ('공인회계사')
) v(interest_name)
WHERE mid.interest_name = '국가자격'

UNION ALL

SELECT
    mid.id,
    3,
    v.interest_name,
    'SYSTEM'::created_source_type,
    false,
    NOW(),
    NOW()
FROM inserted_middle mid
CROSS JOIN LATERAL (VALUES
    -- 어학 자격 능력
    ('토익'),
    ('토익스피킹'),
    ('오픽'),
    ('토플'),
    ('HSK')
) v(interest_name)
WHERE mid.interest_name = '어학 자격 능력'

UNION ALL

SELECT
    mid.id,
    3,
    v.interest_name,
    'SYSTEM'::created_source_type,
    false,
    NOW(),
    NOW()
FROM inserted_middle mid
CROSS JOIN LATERAL (VALUES
    -- 기술 분야
    ('손뜨개전문가'),
    ('청소 전문가'),
    ('반려동물장례지도사'),
    ('베이비시터'),
    ('전기 기사'),
    ('컬러리스트 기사'),
    ('미용사(네일)')
) v(interest_name)
WHERE mid.interest_name = '기술 분야'

UNION ALL

SELECT
    mid.id,
    3,
    v.interest_name,
    'SYSTEM'::created_source_type,
    false,
    NOW(),
    NOW()
FROM inserted_middle mid
CROSS JOIN LATERAL (VALUES
    -- 직무별 프로그램 학습
    ('AI 프롬프트 학습'),
    ('작업용 프로그램 학습')
) v(interest_name)
WHERE mid.interest_name = '직무별 프로그램 학습'

UNION ALL

SELECT
    mid.id,
    3,
    v.interest_name,
    'SYSTEM'::created_source_type,
    false,
    NOW(),
    NOW()
FROM inserted_middle mid
CROSS JOIN LATERAL (VALUES
    -- 커리어 전환 준비
    ('포트폴리오 제작'),
    ('이력서 관리'),
    ('자격증 취득'),
    ('어학 공부')
) v(interest_name)
WHERE mid.interest_name = '커리어 전환 준비'

UNION ALL

SELECT
    mid.id,
    3,
    v.interest_name,
    'SYSTEM'::created_source_type,
    false,
    NOW(),
    NOW()
FROM inserted_middle mid
CROSS JOIN LATERAL (VALUES
    -- 자격증 (직무 관련 역량 개발)
    ('직무 전문 분야 자격증'),
    ('어학')
) v(interest_name)
WHERE mid.interest_name = '자격증' AND mid.parent_id IN (SELECT id FROM inserted_major WHERE interest_name = '직무 관련 역량 개발')

UNION ALL

SELECT
    mid.id,
    3,
    v.interest_name,
    'SYSTEM'::created_source_type,
    false,
    NOW(),
    NOW()
FROM inserted_middle mid
CROSS JOIN LATERAL (VALUES
    -- 업무 능력 향상
    ('업무 효율 높이기 (우선순위/시간관리)'),
    ('커뮤니케이션·협업 능력 키우기'),
    ('문제 해결능력 향상'),
    ('문서·기획·정리 스킬 향상(PPT·보고서)')
) v(interest_name)
WHERE mid.interest_name = '업무 능력 향상';

-- 확인용 쿼리
SELECT
    CASE
        WHEN i.depth = 1 THEN i.interest_name
        ELSE (SELECT interest_name FROM interest WHERE id = i.parent_id)
    END as 대분류,
    CASE
        WHEN i.depth = 2 THEN i.interest_name
        WHEN i.depth = 3 THEN (SELECT interest_name FROM interest WHERE id = i.parent_id)
        ELSE NULL
    END as 중분류,
    CASE
        WHEN i.depth = 3 THEN i.interest_name
        ELSE NULL
    END as 소분류,
    i.depth,
    i.id,
    i.parent_id
FROM interest i
WHERE i.created_source = 'SYSTEM'::created_source_type
ORDER BY
    CASE
        WHEN i.depth = 1 THEN i.id
        WHEN i.depth = 2 THEN i.parent_id
        ELSE (SELECT parent_id FROM interest WHERE id = i.parent_id)
    END,
    CASE
        WHEN i.depth = 2 THEN i.id
        WHEN i.depth = 3 THEN i.parent_id
        ELSE NULL
    END,
    i.id;
