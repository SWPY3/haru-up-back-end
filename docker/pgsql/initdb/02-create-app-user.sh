#!/bin/bash
set -e

echo "Creating application user and database..."
echo "  - Admin user: ${POSTGRES_USER}"
echo "  - App user: haruup_user"

: "${HARUUP_USER_PASSWORD:=${POSTGRES_PASSWORD}}"

########################################
# 1) 유저 존재 확인 후 생성
########################################
USER_EXISTS=$(psql -tAc "SELECT 1 FROM pg_roles WHERE rolname='haruup_user'" -U "${POSTGRES_USER}")

if [ "$USER_EXISTS" != "1" ]; then
  echo "Creating user haruup_user..."
  psql -U "${POSTGRES_USER}" <<EOF
CREATE USER haruup_user
  WITH PASSWORD '${HARUUP_USER_PASSWORD}'
  NOSUPERUSER NOCREATEDB NOCREATEROLE;
EOF
else
  echo "User haruup_user already exists. Skipping."
fi

########################################
# 2) DB 존재 확인 후 생성
########################################
DB_EXISTS=$(psql -tAc "SELECT 1 FROM pg_database WHERE datname='haruup'" -U "${POSTGRES_USER}")

if [ "$DB_EXISTS" != "1" ]; then
  echo "Creating database haruup..."
  psql -U "${POSTGRES_USER}" <<EOF
CREATE DATABASE haruup
  OWNER haruup_user
  TEMPLATE template0
  ENCODING 'UTF8'
  LC_COLLATE 'en_US.utf8'
  LC_CTYPE 'en_US.utf8';
EOF
else
  echo "Database haruup already exists. Skipping."
fi

########################################
# 3) 권한 설정
########################################
echo "Setting privileges on haruup DB..."

psql -U "${POSTGRES_USER}" -d haruup <<EOF
GRANT ALL PRIVILEGES ON SCHEMA public TO haruup_user;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO haruup_user;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO haruup_user;
GRANT ALL PRIVILEGES ON ALL FUNCTIONS IN SCHEMA public TO haruup_user;

ALTER DEFAULT PRIVILEGES IN SCHEMA public
  GRANT ALL PRIVILEGES ON TABLES TO haruup_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
  GRANT ALL PRIVILEGES ON SEQUENCES TO haruup_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
  GRANT ALL PRIVILEGES ON FUNCTIONS TO haruup_user;
EOF

echo "✅ Done."
