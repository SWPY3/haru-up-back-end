package com.haruUp.global.config

import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcTemplate

/**
 * pgvector Extension 설정
 *
 * - PostgreSQL에 pgvector extension 활성화
 * - 벡터 연산을 위한 초기 설정
 */
@Configuration
class PgVectorConfig(
    private val jdbcTemplate: JdbcTemplate
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * pgvector extension 활성화
     */
    @PostConstruct
    fun initializePgVector() {
        try {
            logger.info("pgvector extension 초기화 시작")

            // pgvector extension 생성 (이미 있으면 무시)
            jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS vector")

            logger.info("pgvector extension 활성화 완료")

            // 버전 확인
            val version = jdbcTemplate.queryForObject(
                "SELECT extversion FROM pg_extension WHERE extname = 'vector'",
                String::class.java
            )
            logger.info("pgvector 버전: $version")

        } catch (e: Exception) {
            logger.error("pgvector extension 초기화 실패: ${e.message}", e)
            logger.warn("pgvector가 설치되어 있는지 확인하세요: https://github.com/pgvector/pgvector")
        }
    }
}
