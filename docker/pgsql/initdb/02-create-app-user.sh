#!/bin/bash
set -e

echo "Creating application user and database..."

# 애플리케이션 사용자 및 데이터베이스 생성
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    -- 애플리케이션 사용자 생성 (DB 생성 권한 없음, 슈퍼유저 아님)
    CREATE USER haruup_user WITH
        PASSWORD '${HARUUP_USER_PASSWORD}'
        NOCREATEDB
        NOCREATEROLE
        NOSUPERUSER;

    -- 애플리케이션 데이터베이스 생성
    CREATE DATABASE haruup
        OWNER haruup_user
        ENCODING 'UTF8'
        LC_COLLATE 'C'
        LC_CTYPE 'ko_KR.UTF-8'
        TEMPLATE template0;

    -- haruup_user가 다른 데이터베이스에 접근하지 못하도록 기본 권한 제거
    REVOKE CONNECT ON DATABASE postgres FROM PUBLIC;
    REVOKE CONNECT ON DATABASE postgres FROM haruup_user;

    -- haruup 데이터베이스에만 접근 권한 부여
    GRANT CONNECT ON DATABASE haruup TO haruup_user;

    -- postgres 관리자 계정은 모든 DB 접근 가능하도록 유지
    GRANT CONNECT ON DATABASE postgres TO postgres;
EOSQL

# haruup 데이터베이스에 연결하여 스키마 권한 설정
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "haruup" <<-EOSQL
    -- public 스키마에 대한 모든 권한 부여
    GRANT ALL PRIVILEGES ON SCHEMA public TO haruup_user;

    -- 기존 테이블에 대한 권한 (있을 경우)
    GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO haruup_user;
    GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO haruup_user;
    GRANT ALL PRIVILEGES ON ALL FUNCTIONS IN SCHEMA public TO haruup_user;

    -- 향후 생성될 테이블에 대한 기본 권한 설정
    ALTER DEFAULT PRIVILEGES IN SCHEMA public
        GRANT ALL PRIVILEGES ON TABLES TO haruup_user;
    ALTER DEFAULT PRIVILEGES IN SCHEMA public
        GRANT ALL PRIVILEGES ON SEQUENCES TO haruup_user;
    ALTER DEFAULT PRIVILEGES IN SCHEMA public
        GRANT ALL PRIVILEGES ON FUNCTIONS TO haruup_user;
EOSQL

echo "✅ Application user 'haruup_user' and database 'haruup' created successfully!"
echo "   - haruup_user can ONLY access 'haruup' database"
echo "   - haruup_user has full privileges on 'haruup' database"
echo "   - haruup_user cannot access 'postgres' or other databases"

