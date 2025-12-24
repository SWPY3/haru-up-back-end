package com.haruUp.interest

import com.haruUp.HaruUpApplication
import com.haruUp.interest.dto.InterestLevel
import com.haruUp.interest.service.InterestEmbeddingInitializer
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.boot.SpringApplication
import org.springframework.context.ConfigurableApplicationContext
import kotlin.system.exitProcess

/**
 * 관심사 임베딩 초기화 실행 Runner
 *
 * 명령줄에서 실행하여 관심사 임베딩을 초기화합니다.
 *
 * ## 사용법:
 *
 * # Gradle 태스크 호출
 * ./gradlew initEmbeddings -Pargs="init-embeddings"
 * ./gradlew initEmbeddings -Pargs="init-embeddings --force"
 * ./gradlew initEmbeddings -Pargs="init-embeddings --level=MAIN"
 * ./gradlew initEmbeddings -Pargs="init-embeddings --level=MIDDLE --source=AI --force"
 * ```
 *
 * ## 옵션:
 * - `--force, -f`: 이미 임베딩된 항목도 재생성
 * - `--level=LEVEL`: 특정 레벨만 초기화 (MAIN, MIDDLE, SUB)
 * - `--source=SOURCE`: 특정 created_source만 재임베딩 (AI, SYSTEM, USER 등)
 */
object InitEmbeddingsRunner {
    private val logger = LoggerFactory.getLogger(javaClass)

    @JvmStatic
    fun main(args: Array<String>) {
        logger.info("=== 관심사 임베딩 초기화 실행 ===")
        logger.info("인자: ${args.joinToString(", ")}")

        // Spring 컨텍스트 부트스트랩
        val context: ConfigurableApplicationContext = SpringApplication.run(HaruUpApplication::class.java, *args)

        try {
            val embeddingInitializer = context.getBean(InterestEmbeddingInitializer::class.java)

            // 파라미터 파싱
            val forceUpdate = args.contains("--force") || args.contains("-f")
            val levelArg = args.find { it.startsWith("--level=") }
            val level = levelArg?.substringAfter("=")
            val sourceArg = args.find { it.startsWith("--source=") }
            val source = sourceArg?.substringAfter("=")

            logger.info("파라미터:")
            logger.info("  - forceUpdate: $forceUpdate")
            logger.info("  - level: ${level ?: "ALL"}")
            logger.info("  - source: ${source ?: "ALL"}")

            runBlocking {
                val result = if (level != null) {
                    // 특정 레벨만 초기화
                    val interestLevel = try {
                        InterestLevel.valueOf(level.uppercase())
                    } catch (e: IllegalArgumentException) {
                        logger.error("잘못된 레벨: $level (사용 가능: MAIN, MIDDLE, SUB)")
                        context.close()
                        exitProcess(1)
                    }
                    embeddingInitializer.initializeByLevel(interestLevel, forceUpdate, source)
                } else {
                    // 전체 초기화
                    embeddingInitializer.initializeAllEmbeddings(forceUpdate, source)
                }

                // 결과 출력
                logger.info("")
                logger.info("=== 임베딩 초기화 완료 ===")
                logger.info(result.summary())
                logger.info("")

                context.close()

                if (result.failCount > 0) {
                    logger.warn("일부 항목이 실패했습니다. 로그를 확인하세요.")
                    exitProcess(1)
                }

                exitProcess(0)
            }
        } catch (e: Exception) {
            logger.error("임베딩 초기화 실패: ${e.message}", e)
            context.close()
            exitProcess(1)
        }
    }
}