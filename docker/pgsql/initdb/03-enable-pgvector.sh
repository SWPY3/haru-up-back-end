#!/bin/bash
set -e

echo "Enabling pgvector extension..."

# haruup 데이터베이스에 pgvector 확장 설치 (postgres 슈퍼유저로 실행)
psql -v ON_ERROR_STOP=1 --username "postgres" --dbname "haruup" <<-EOSQL
    -- pgvector 확장 활성화
    CREATE EXTENSION IF NOT EXISTS vector;

    -- 확장이 성공적으로 설치되었는지 확인
    SELECT extname, extversion FROM pg_extension WHERE extname = 'vector';
EOSQL

echo "✅ pgvector extension enabled successfully in 'haruup' database!"
echo "   - You can now use vector data type and similarity search functions"
