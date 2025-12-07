#!/bin/bash
set -e

echo "Creating application user and database..."
echo "  - Admin user (POSTGRES_USER): ${POSTGRES_USER}"
echo "  - Application user: haruup_user (non-superuser)"

# 애플리케이션 계정 비밀번호: 없으면 POSTGRES_PASSWORD를 기본값으로 사용
: "${HARUUP_USER_PASSWORD:=${POSTGRES_PASSWORD}}"

# 1) 클러스터 admin 계정(POSTGRES_USER)으로 접속해서 사용자/DB 생성
psql -v ON_ERROR_STOP=1 --username "${POSTGRES_USER}" --dbname "${POSTGRES_DB}" <<-EOSQL
    -- 1. 애플리케이션 일반 사용자 생성 (non-superuser)
    CREATE USER ${HARUUP_USER} WITH
        PASSWORD '${HARUUP_USER_PASSWORD}'
        NOCREATEDB
        NOCREATEROLE
        NOSUPERUSER;

    -- 2. 애플리케이션 데이터베이스 생성 (owner: HARUUP_USER)
    CREATE DATABASE ${HARUUP_DB}
        OWNER ${HARUUP_USER}
        ENCODING 'UTF8'
        LC_COLLATE 'en_US.utf8'
        LC_CTYPE 'en_US.utf8'
        TEMPLATE template0;

    -- 3. 보안 설정: HARUUP_USER는 HARUUP_DB만 접근 가능
    REVOKE CONNECT ON DATABASE ${POSTGRES_DB} FROM PUBLIC;
    REVOKE CONNECT ON DATABASE ${POSTGRES_DB} FROM ${HARUUP_USER};
    GRANT CONNECT ON DATABASE ${HARUUP_DB} TO ${HARUUP_USER};
EOSQL

# 2) HARUUP_DB에서 스키마/오브젝트 권한 설정
psql -v ON_ERROR_STOP=1 --username "${POSTGRES_USER}" --dbname "${HARUUP_DB}" <<-EOSQL
    -- HARUUP_USER에게 public 스키마의 모든 권한 부여
    GRANT ALL PRIVILEGES ON SCHEMA public TO ${HARUUP_USER};

    -- 기존 객체에 대한 권한 부여
    GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO ${HARUUP_USER};
    GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO ${HARUUP_USER};
    GRANT ALL PRIVILEGES ON ALL FUNCTIONS IN SCHEMA public TO ${HARUUP_USER};

    -- 향후 생성될 객체에 대한 기본 권한 설정
    ALTER DEFAULT PRIVILEGES IN SCHEMA public
        GRANT ALL PRIVILEGES ON TABLES TO ${HARUUP_USER};
    ALTER DEFAULT PRIVILEGES IN SCHEMA public
        GRANT ALL PRIVILEGES ON SEQUENCES TO ${HARUUP_USER};
    ALTER DEFAULT PRIVILEGES IN SCHEMA public
        GRANT ALL PRIVILEGES ON FUNCTIONS TO ${HARUUP_USER};
EOSQL

echo ""
echo "✅ Database setup completed successfully!"
echo ""
echo "  📊 Database Configuration:"
echo "    - Admin user (POSTGRES_USER): ${POSTGRES_USER}"
echo "    - Application user:           ${HARUUP_USER} (password: \$HARUUP_USER_PASSWORD)"
echo "    - Application DB:             ${HARUUP_DB} (owner: ${HARUUP_USER})"
echo ""
echo "  🔒 Security:"
echo "    - ${HARUUP_USER} can ONLY access '${HARUUP_DB}' database"
echo "    - ${HARUUP_USER} has full privileges on '${HARUUP_DB}' database"
echo ""
