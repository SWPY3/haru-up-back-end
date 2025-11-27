#!/bin/bash
set -e

# pg_hba.conf를 수정하여 비밀번호 인증을 활성화합니다
echo "Configuring PostgreSQL authentication..."

cat > ${PGDATA}/pg_hba.conf << 'EOF'
# PostgreSQL Client Authentication Configuration File
# TYPE  DATABASE        USER            ADDRESS                 METHOD

# "local" is for Unix domain socket connections only
local   all             all                                     scram-sha-256

# IPv4 local connections:
host    all             all             127.0.0.1/32            scram-sha-256

# IPv6 local connections:
host    all             all             ::1/128                 scram-sha-256

# Allow replication connections from localhost, by a user with the
# replication privilege.
local   replication     all                                     scram-sha-256
host    replication     all             127.0.0.1/32            scram-sha-256
host    replication     all             ::1/128                 scram-sha-256

# 외부 접속 허용 (Docker 네트워크에서)
host    all             all             all                     scram-sha-256
EOF

echo "PostgreSQL authentication configured successfully!"

