package com.haruUp.member.application.useCase

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.util.JSONPObject
import com.haruUp.category.application.JobDetailService
import com.haruUp.category.application.JobService
import com.haruUp.character.application.service.CharacterService
import com.haruUp.character.application.service.LevelService
import com.haruUp.character.application.service.MemberCharacterService
import com.haruUp.global.clova.MissionMemberProfile
import com.haruUp.interest.dto.InterestPathDto
import com.haruUp.interest.entity.MemberInterestEntity
import com.haruUp.interest.model.InterestPath
import com.haruUp.interest.repository.InterestEmbeddingJpaRepository
import com.haruUp.interest.repository.MemberInterestJpaRepository
import com.haruUp.member.application.event.CurationLogEvent
import com.haruUp.member.application.service.MemberProfileService
import com.haruUp.member.domain.dto.MemberProfileDto
import com.haruUp.member.infrastructure.MemberProfileRepository
import com.haruUp.mission.application.MissionRecommendService
import com.haruUp.mission.domain.MemberMission
import com.haruUp.mission.domain.MissionStatus
import com.haruUp.mission.infrastructure.MemberMissionRepository
import com.haruUp.missionembedding.dto.MissionRecommendationRequest
import com.haruUp.missionembedding.dto.MissionRecommendationResponse
import com.haruUp.missionembedding.service.MissionRecommendationService
import jakarta.transaction.Transactional
import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.LocalDateTime


@Component
class MemberCurationUseCase(
    private val characterService: CharacterService,
    private val levelService: LevelService,
    private val memberCharacterService: MemberCharacterService,
    private val memberProfileService: MemberProfileService,
    private val missionRecommendationService: MissionRecommendationService,
    private val memberInterestRepository: MemberInterestJpaRepository,
    private val memberProfileRepository: MemberProfileRepository,
    private val interestEmbeddingRepository: InterestEmbeddingJpaRepository,
    private val memberMissionRepository: MemberMissionRepository,
    private val objectMapper : ObjectMapper,
    private val jobService: JobService,
    private val jobDetailService: JobDetailService,
    private val missionRecommendService: MissionRecommendService
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional
    suspend fun runInitialCuration(
        characterId: Long,
        memberProfileDto: MemberProfileDto,
        interests: List<InterestPathDto>,
        onLog: suspend (CurationLogEvent) -> Unit
    ) {
        val memberId = memberProfileDto.memberId
            ?: throw IllegalArgumentException("존재하지 않는 회원입니다.")

        /** STEP 01 캐릭터 */
        characterService.validateExists(characterId)
        val levelId = levelService.getInitialLevelId()
        memberCharacterService.createInitial(memberId, characterId, levelId)
        onLog(CurationLogEvent("회원 캐릭터 정보 생성 완료", ""))
        delaySafe()

        /** STEP 02 프로필 */
        memberProfileService.curationUpdateProfile(memberId, memberProfileDto)
        onLog(CurationLogEvent("회원 기본 프로필 저장 완료", ""))
        delaySafe()

        /** STEP 03 직업 */
        memberProfileDto.jobId?.let {
            memberProfileService.memberJobUpdate(memberId, it)
            onLog(CurationLogEvent("회원 직업 정보 설정 완료", ""))
            delaySafe()
        }

        /** STEP 04 직업 상세 */
        memberProfileDto.jobDetailId?.let {
            memberProfileService.memberJobDetailUpdate(memberId, it)
            onLog(CurationLogEvent("회원 직업 상세 정보 설정 완료", ""))
            delaySafe()
        }

        /** STEP 05 관심사 */
        val memberInterestIds = saveInterests(interests, memberId)
        onLog(CurationLogEvent("회원 관심사 설정 완료", memberInterestIds.toString()))
        delaySafe()

        /** STEP 06 미션 추천 */
        val request = MissionRecommendationRequest(memberInterestIds)
        val missions = recommendMissions(memberId, request)


        /** MissionRecommendationResponse → JSON 문자열 */
        val missionsJson: String = objectMapper.writeValueAsString(missions)
        onLog( CurationLogEvent( step = "회원 미션 설정 완료", message = missionsJson ) )
        delaySafe()


        onLog(CurationLogEvent("초기 회원 설정 완료", ""))
    }

    /** 관심사 저장 */
    private fun saveInterests(
        interests: List<InterestPathDto>,
        memberId: Long
    ): List<Long> {

        val ids = mutableListOf<Long>()

        interests.forEach { interestPath ->
            if (interestPath.directFullPath.size != 3) {
                logger.warn("관심사 경로 오류: {}", interestPath.directFullPath)
                return@forEach
            }

            val fullPathStr = "{${interestPath.directFullPath.joinToString(",")}}"
            val interestId = interestEmbeddingRepository.findIdByFullPath(fullPathStr)
                ?: return@forEach

            if (!memberInterestRepository.existsByMemberIdAndInterestId(memberId, interestId)) {
                val saved : MemberInterestEntity = memberInterestRepository.save(
                    MemberInterestEntity(
                        memberId = memberId,
                        interestId = interestId,
                        directFullPath = interestPath.directFullPath
                    )
                )

                println("saved : $saved")

                saved.id?.let(ids::add)
            }
        }

        return ids
    }



    /** 미션 추천 */
    suspend fun recommendMissions(
        memberId: Long,
        request: MissionRecommendationRequest
    ): MissionRecommendationResponse {

        val memberInterests = request.memberInterestIds.mapNotNull {
            memberInterestRepository.findById(it).orElse(null)
                ?.takeIf { mi -> mi.memberId == memberId }
        }

        require(memberInterests.isNotEmpty()) { "유효한 관심사가 없습니다." }

        val profile = memberProfileRepository.findByMemberId(memberId)
            ?: throw IllegalArgumentException("프로필 없음")

        val missionMemberProfile = MissionMemberProfile(
            age = profile.birthDt?.let { missionRecommendService.calculateAge(it) },
            gender = profile.gender?.name,
            jobName = profile.jobId?.let { jobService.getJob(it).jobName },
            jobDetailName = profile.jobDetailId?.let { jobDetailService.getJobDetail(it).jobDetailName }
        )

        val interests = memberInterests.map {
            Pair(
                it.id!!.toInt(),
                InterestPath(
                    it.directFullPath!![0],
                    it.directFullPath!!.getOrNull(1),
                    it.directFullPath!!.getOrNull(2)
                )
            )
        }

        val missions = missionRecommendationService.recommendMissions(interests, missionMemberProfile)

        memberInterests.forEach {
            memberMissionRepository.softDeleteByMemberIdAndInterestIdAndStatus(
                memberId,
                it.id!!,
                MissionStatus.READY,
                LocalDateTime.now()
            )
        }

        missions.forEach { group ->
            group.data.forEach { mission ->
                group.memberInterestId?.let {
                    memberMissionRepository.save(
                        MemberMission(
                            memberId = memberId,
                            missionId = mission.mission_id!!,
                            memberInterestId = it.toLong(),
                            missionStatus = MissionStatus.READY,
                            expEarned = 0
                        )
                    )
                }
            }
        }

        return MissionRecommendationResponse(
            missions = missions,
            totalCount = missions.sumOf { it.data.size }
        )
    }

    private suspend fun delaySafe() {
        delay(700)
    }
}