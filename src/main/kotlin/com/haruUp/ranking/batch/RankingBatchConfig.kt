package com.haruUp.ranking.batch

import com.haruUp.interest.repository.InterestEmbeddingJpaRepository
import com.haruUp.interest.repository.MemberInterestJpaRepository
import com.haruUp.member.infrastructure.MemberProfileRepository
import com.haruUp.mission.domain.MemberMissionEntity
import com.haruUp.mission.infrastructure.MemberMissionRepository
import com.haruUp.ranking.domain.RankingMissionDailyEntity
import com.haruUp.ranking.repository.RankingMissionDailyRepository
import org.slf4j.LoggerFactory
import org.springframework.batch.core.Job
import org.springframework.batch.core.Step
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.launch.support.RunIdIncrementer
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.step.builder.StepBuilder
import org.springframework.batch.item.ItemProcessor
import org.springframework.batch.item.ItemReader
import org.springframework.batch.item.ItemWriter
import org.springframework.batch.item.support.ListItemReader
import org.springframework.beans.factory.annotation.Value
import org.springframework.batch.core.configuration.annotation.StepScope
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.repository.findByIdOrNull
import org.springframework.transaction.PlatformTransactionManager
import java.time.LocalDate

@Configuration
class RankingBatchConfig(
    private val memberMissionRepository: MemberMissionRepository,
    private val memberInterestRepository: MemberInterestJpaRepository,
    private val interestEmbeddingRepository: InterestEmbeddingJpaRepository,
    private val memberProfileRepository: MemberProfileRepository,
    private val rankingMissionDailyRepository: RankingMissionDailyRepository,
    private val rankingLabelService: RankingLabelService
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        const val JOB_NAME = "rankingMissionDailyJob"
        const val STEP_NAME = "rankingMissionDailyStep"
        const val CHUNK_SIZE = 100
    }

    @Value("\${ranking.batch.target-date:#{null}}")
    private var targetDateParam: String? = null

    private fun getTargetDate(): LocalDate {
        return targetDateParam?.let { LocalDate.parse(it) } ?: LocalDate.now()
    }

    /**
     * 랭킹 배치 Job 정의
     */
    @Bean
    fun rankingMissionDailyJob(
        jobRepository: JobRepository,
        rankingMissionDailyStep: Step
    ): Job {
        return JobBuilder(JOB_NAME, jobRepository)
            .incrementer(RunIdIncrementer())
            .start(rankingMissionDailyStep)
            .build()
    }

    /**
     * 랭킹 배치 Step 정의
     */
    @Bean
    fun rankingMissionDailyStep(
        jobRepository: JobRepository,
        transactionManager: PlatformTransactionManager,
        reader: ItemReader<MemberMissionEntity>,
        processor: ItemProcessor<MemberMissionEntity, RankingMissionDailyEntity>,
        writer: ItemWriter<RankingMissionDailyEntity>
    ): Step {
        return StepBuilder(STEP_NAME, jobRepository)
            .chunk<MemberMissionEntity, RankingMissionDailyEntity>(CHUNK_SIZE, transactionManager)
            .reader(reader)
            .processor(processor)
            .writer(writer)
            .build()
    }

    /**
     * ItemReader: 선택된 미션 중 아직 처리되지 않은 것들 조회
     */
    @Bean
    @StepScope
    fun rankingMissionItemReader(): ItemReader<MemberMissionEntity> {
        val targetDate = getTargetDate()
        logger.info("랭킹 배치 Reader 시작 - targetDate: $targetDate")

        // 1. 오늘 선택된 미션 조회
        val selectedMissions = memberMissionRepository.findSelectedMissionsByTargetDate(targetDate)
        logger.info("선택된 미션 수: ${selectedMissions.size}")

        // 2. 이미 처리된 member_mission_id 제외
        val missionIds = selectedMissions.mapNotNull { it.id }
        logger.info("선택된 미션 ID 목록: $missionIds")

        val existingRecords = if (selectedMissions.isNotEmpty()) {
            rankingMissionDailyRepository.findAllByMemberMissionIdIn(missionIds)
        } else {
            emptyList()
        }
        logger.info("ranking_mission_daily 조회 결과: ${existingRecords.size}건, IDs: ${existingRecords.map { it.memberMissionId }}")

        val existingMemberMissionIds = existingRecords.map { it.memberMissionId }.toSet()

        val targetMissions = selectedMissions.filter { it.id !in existingMemberMissionIds }
        logger.info("처리 대상 미션 수: ${targetMissions.size} (이미 처리됨: ${existingMemberMissionIds.size})")

        return ListItemReader(targetMissions)
    }

    /**
     * ItemProcessor: 라벨 생성 및 데이터 변환
     */
    @Bean
    @StepScope
    fun rankingMissionItemProcessor(): ItemProcessor<MemberMissionEntity, RankingMissionDailyEntity> {
        return ItemProcessor { memberMission ->
            try {
                val memberMissionId = memberMission.id ?: return@ItemProcessor null
                val targetDate = getTargetDate()

                logger.info("Processing memberMissionId=$memberMissionId, missionContent=${memberMission.missionContent}")

                // member_interest -> interest_embedding 조회
                val memberInterest = memberInterestRepository.findByIdOrNull(memberMission.memberInterestId)
                val interestEmbedding = memberInterest?.let {
                    interestEmbeddingRepository.findByIdOrNull(it.interestId)
                }

                // member_profile 조회
                val memberProfile = memberProfileRepository.findByMemberId(memberMission.memberId)

                // 라벨 처리 (별도 서비스에서 @Transactional 처리)
                val labelName = rankingLabelService.processLabel(
                    memberMissionId = memberMissionId,
                    existingLabelName = memberMission.labelName,
                    missionContent = memberMission.missionContent,
                    interestPath = interestEmbedding?.fullPath ?: memberInterest?.directFullPath
                )

                logger.info("라벨 처리 완료: memberMissionId=$memberMissionId, labelName=$labelName")

                RankingMissionDailyEntity(
                    rankingDate = targetDate,
                    memberMissionId = memberMissionId,
                    interestFullPath = interestEmbedding?.fullPath ?: memberInterest?.directFullPath,
                    gender = memberProfile?.gender,
                    birthDt = memberProfile?.birthDt?.toLocalDate(),
                    jobId = memberProfile?.jobId,
                    jobDetailId = memberProfile?.jobDetailId,
                    labelName = labelName
                )
            } catch (e: Exception) {
                logger.error("처리 실패: memberMissionId=${memberMission.id}, error=${e.message}", e)
                null
            }
        }
    }

    /**
     * ItemWriter: ranking_mission_daily 테이블에 저장
     */
    @Bean
    @StepScope
    fun rankingMissionItemWriter(): ItemWriter<RankingMissionDailyEntity> {
        return ItemWriter { items ->
            logger.info("랭킹 데이터 저장: ${items.size()}건")
            rankingMissionDailyRepository.saveAll(items)
        }
    }
}
