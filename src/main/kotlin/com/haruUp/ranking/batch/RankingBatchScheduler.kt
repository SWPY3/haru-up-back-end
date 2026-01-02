package com.haruUp.ranking.batch

import org.slf4j.LoggerFactory
import org.springframework.batch.core.Job
import org.springframework.batch.core.JobParametersBuilder
import org.springframework.batch.core.launch.JobLauncher
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDate

/**
 * 랭킹 배치 스케줄러
 * 매일 지정된 시간에 전날 기준 랭킹 집계 배치 실행
 */
@Component
class RankingBatchScheduler(
    private val jobLauncher: JobLauncher,
    @Qualifier("rankingMissionDailyJob")
    private val rankingMissionDailyJob: Job
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Value("\${ranking.batch.enabled:true}")
    private var batchEnabled: Boolean = true

    /**
     * 매일 00시에 실행 (전날 데이터 기준)
     * cron: 초 분 시 일 월 요일
     */
    @Scheduled(cron = "\${ranking.batch.cron:0 0 0 * * *}")
    fun executeRankingBatchJob() {

        if (!batchEnabled) {
            logger.info("랭킹 배치 스케줄러가 비활성화되어 있습니다.")
            return
        }

        val targetDate = LocalDate.now().minusDays(1)

        logger.info("랭킹 배치 스케줄러 시작 - targetDate={}", targetDate)

        try {
            val jobParameters = JobParametersBuilder()
                // ✅ 날짜는 JobInstance 식별자
                .addString("targetDate", targetDate.toString(), true)

                // ✅ 재실행을 위한 파라미터 (식별자 ❌)
                .addLong("timestamp", System.currentTimeMillis(), false)

                .toJobParameters()

            val jobExecution = jobLauncher.run(
                rankingMissionDailyJob,
                jobParameters
            )

            logger.info(
                "랭킹 배치 스케줄러 완료 - jobId={}, status={}, exitStatus={}",
                jobExecution.jobId,
                jobExecution.status,
                jobExecution.exitStatus.exitCode
            )

        } catch (e: Exception) {
            logger.error(
                "랭킹 배치 스케줄러 실패 - targetDate={}, message={}",
                targetDate,
                e.message,
                e
            )
        }
    }
}