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
    CREATE USER haruup_user WITH
        PASSWORD '${HARUUP_USER_PASSWORD}'
        NOCREATEDB
        NOCREATEROLE
        NOSUPERUSER;

    -- 2. 애플리케이션 데이터베이스 생성 (owner: haruup_user)
    CREATE DATABASE haruup
        OWNER haruup_user
        ENCODING 'UTF8'
        LC_COLLATE 'en_US.utf8'
        LC_CTYPE 'en_US.utf8'
        TEMPLATE template0;

    -- 3. 보안 설정: haruup_user는 haruup DB만 접근 가능
    REVOKE CONNECT ON DATABASE ${POSTGRES_DB} FROM PUBLIC;
    REVOKE CONNECT ON DATABASE ${POSTGRES_DB} FROM haruup_user;
    GRANT CONNECT ON DATABASE haruup TO haruup_user;
EOSQL

# 2) haruup DB에서 스키마/오브젝트 권한 설정
psql -v ON_ERROR_STOP=1 --username "${POSTGRES_USER}" --dbname "haruup" <<-EOSQL
    -- haruup_user에게 public 스키마의 모든 권한 부여
    GRANT ALL PRIVILEGES ON SCHEMA public TO haruup_user;

    -- 기존 객체에 대한 권한 부여
    GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO haruup_user;
    GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO haruup_user;
    GRANT ALL PRIVILEGES ON ALL FUNCTIONS IN SCHEMA public TO haruup_user;

    -- 향후 생성될 객체에 대한 기본 권한 설정
    ALTER DEFAULT PRIVILEGES IN SCHEMA public
        GRANT ALL PRIVILEGES ON TABLES TO haruup_user;
    ALTER DEFAULT PRIVILEGES IN SCHEMA public
        GRANT ALL PRIVILEGES ON SEQUENCES TO haruup_user;
    ALTER DEFAULT PRIVILEGES IN SCHEMA public
        GRANT ALL PRIVILEGES ON FUNCTIONS TO haruup_user;
EOSQL

echo ""
echo "✅ Database setup completed successfully!"
echo ""
echo "  📊 Database Configuration:"
echo "    - Admin user (POSTGRES_USER): ${POSTGRES_USER}"
echo "    - Application user:           haruup_user (password: \$HARUUP_USER_PASSWORD)"
echo "    - Application DB:             haruup (owner: haruup_user)"
echo ""
echo "  🔒 Security:"
echo "    - haruup_user can ONLY access 'haruup' database"
echo "    - haruup_user has full privileges on 'haruup' database"
echo ""
