#!/bin/bash
set -e

echo "Creating application user and database..."
echo "  - Superuser: postgres (admin)"
echo "  - Application user: haruup_user (non-superuser)"

# postgres 슈퍼유저로 postgres 데이터베이스에 연결하여 사용자 및 DB 생성
psql -v ON_ERROR_STOP=1 --username "postgres" --dbname "postgres" <<-EOSQL
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
    REVOKE CONNECT ON DATABASE postgres FROM PUBLIC;
    REVOKE CONNECT ON DATABASE postgres FROM haruup_user;
    GRANT CONNECT ON DATABASE haruup TO haruup_user;

    -- 4. postgres 슈퍼유저는 모든 DB 접근 가능 (기본 유지)
    GRANT CONNECT ON DATABASE postgres TO postgres;
EOSQL

# postgres 슈퍼유저로 haruup 데이터베이스에 연결하여 스키마 권한 설정
psql -v ON_ERROR_STOP=1 --username "postgres" --dbname "haruup" <<-EOSQL
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
echo "    - Superuser:        postgres (password: set in .env)"
echo "    - Application user: haruup_user (password: set in .env)"
echo "    - Application DB:   haruup (owner: haruup_user)"
echo ""
echo "  🔒 Security:"
echo "    - haruup_user can ONLY access 'haruup' database"
echo "    - haruup_user has full privileges on 'haruup' database"
echo "    - haruup_user cannot access 'postgres' or other databases"
echo "    - postgres user can access all databases"
echo ""

