package com.haruUp.ranking.batch

import com.haruUp.global.clova.ClovaApiClient
import com.haruUp.interest.repository.InterestEmbeddingJpaRepository
import com.haruUp.interest.repository.MemberInterestJpaRepository
import com.haruUp.member.infrastructure.MemberProfileRepository
import com.haruUp.mission.domain.MemberMissionEntity
import com.haruUp.mission.infrastructure.MemberMissionRepository
import com.haruUp.missionembedding.repository.MissionEmbeddingRepository
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
import java.time.LocalDateTime

@Configuration
class RankingBatchConfig(
    private val memberMissionRepository: MemberMissionRepository,
    private val missionEmbeddingRepository: MissionEmbeddingRepository,
    private val memberInterestRepository: MemberInterestJpaRepository,
    private val interestEmbeddingRepository: InterestEmbeddingJpaRepository,
    private val memberProfileRepository: MemberProfileRepository,
    private val rankingMissionDailyRepository: RankingMissionDailyRepository,
    private val clovaApiClient: ClovaApiClient
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        const val JOB_NAME = "rankingMissionDailyJob"
        const val STEP_NAME = "rankingMissionDailyStep"
        const val CHUNK_SIZE = 100
        const val SIMILARITY_THRESHOLD = 0.3
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
        val existingMemberMissionIds = if (selectedMissions.isNotEmpty()) {
            rankingMissionDailyRepository.findAllByMemberMissionIdIn(
                selectedMissions.mapNotNull { it.id }
            ).map { it.memberMissionId }.toSet()
        } else {
            emptySet()
        }

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
                val missionId = memberMission.missionId
                val targetDate = getTargetDate()

                // mission_embedding 조회
                val missionEmbedding = missionEmbeddingRepository.findByIdOrNull(missionId)
                    ?: return@ItemProcessor null

                // member_interest -> interest_embedding 조회
                val memberInterest = memberInterestRepository.findByIdOrNull(memberMission.memberInterestId)
                val interestEmbedding = memberInterest?.let {
                    interestEmbeddingRepository.findByIdOrNull(it.interestId)
                }

                // member_profile 조회
                val memberProfile = memberProfileRepository.findByMemberId(memberMission.memberId)

                // 라벨 처리
                val labelName = processLabel(
                    missionId = missionId,
                    existingLabel = missionEmbedding.labelName,
                    missionContent = missionEmbedding.missionContent,
                    embedding = missionEmbedding.embedding,
                    interestPath = interestEmbedding?.fullPath ?: memberInterest?.directFullPath
                )

                RankingMissionDailyEntity(
                    rankingDate = targetDate,
                    memberMissionId = memberMissionId,
                    missionId = missionId,
                    labelName = labelName,
                    interestFullPath = interestEmbedding?.fullPath ?: memberInterest?.directFullPath,
                    gender = memberProfile?.gender,
                    birthDt = memberProfile?.birthDt?.toLocalDate(),
                    jobId = memberProfile?.jobId,
                    jobDetailId = memberProfile?.jobDetailId
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

    /**
     * 라벨 처리 로직
     */
    private fun processLabel(
        missionId: Long,
        existingLabel: String?,
        missionContent: String,
        embedding: String?,
        interestPath: List<String>?
    ): String? {
        // 1. 기존 라벨이 있으면 그대로 사용
        if (!existingLabel.isNullOrBlank()) {
            return existingLabel
        }

        // 2. 임베딩이 있으면 유사도로 기존 라벨 검색
        if (!embedding.isNullOrBlank()) {
            val similarMission = missionEmbeddingRepository.findSimilarMissionWithLabel(
                embedding = embedding,
                threshold = SIMILARITY_THRESHOLD
            )

            if (similarMission?.labelName != null) {
                val reusedLabel = similarMission.labelName
                missionEmbeddingRepository.updateLabelName(
                    id = missionId,
                    labelName = reusedLabel!!,
                    updatedAt = LocalDateTime.now()
                )
                logger.debug("기존 라벨 재사용: missionId=$missionId, label=$reusedLabel")
                return reusedLabel
            }
        }

        // 3. LLM으로 새 라벨 생성
        return try {
            val newLabel = generateLabelWithLLM(missionContent, interestPath)
            if (!newLabel.isNullOrBlank()) {
                missionEmbeddingRepository.updateLabelName(
                    id = missionId,
                    labelName = newLabel,
                    updatedAt = LocalDateTime.now()
                )
                logger.debug("새 라벨 생성: missionId=$missionId, label=$newLabel")
            }
            newLabel
        } catch (e: Exception) {
            logger.error("라벨 생성 실패: missionId=$missionId, error=${e.message}", e)
            null
        }
    }

    private fun generateLabelWithLLM(missionContent: String, interestPath: List<String>?): String? {
        val prompt = """
            미션 내용을 분석하여 대표 라벨(그룹명)을 생성해주세요.

            [규칙]
            - 구체적인 숫자, 시간, 횟수는 제외하고 핵심 행동만 추출
            - 10자 이내의 간결한 명사형으로 작성
            - 따옴표, 설명 없이 라벨명만 출력

            [예시]
            - "영어 단어 20개 외우기" → 영어 단어 외우기
            - "30분 조깅하기" → 조깅하기
            - "물 2L 마시기" → 물 마시기

            [관심사 경로]
            ${interestPath?.joinToString(" > ") ?: "없음"}

            [미션 내용]
            $missionContent

            라벨:
        """.trimIndent()

        val response = clovaApiClient.generateText(
            userMessage = prompt,
            systemMessage = "당신은 미션 내용을 분석하여 대표 라벨(그룹명)을 생성하는 전문가입니다.",
            temperature = 0.3
        )

        return response.trim()
            .replace("\"", "")
            .replace("'", "")
            .take(100)
            .ifBlank { null }
    }
}
