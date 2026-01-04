package com.haruUp.member.application.useCase

import com.fasterxml.jackson.databind.ObjectMapper
import com.haruUp.character.application.service.CharacterService
import com.haruUp.character.application.service.LevelService
import com.haruUp.character.application.service.MemberCharacterService
import com.haruUp.interest.dto.InterestsDto
import com.haruUp.interest.service.MemberInterestService
import com.haruUp.member.application.event.CurationLogEvent
import com.haruUp.member.application.service.MemberProfileService
import com.haruUp.member.domain.dto.MemberProfileDto
import com.haruUp.mission.application.MissionRecommendService
import jakarta.transaction.Transactional
import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component


/**
 * 초기 큐레이션 결과
 */
data class CurationResult(
    val memberInterestIds: List<Long>
)

@Component
class MemberCurationUseCase(
    private val characterService: CharacterService,
    private val levelService: LevelService,
    private val memberCharacterService: MemberCharacterService,
    private val memberProfileService: MemberProfileService,
    private val memberInterestService: MemberInterestService,
    private val missionRecommendService: MissionRecommendService,
    private val objectMapper: ObjectMapper
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional
    suspend fun runInitialCuration(
        characterId: Long,
        memberProfileDto: MemberProfileDto,
        interests: List<InterestsDto>,
        onLog: suspend (CurationLogEvent) -> Unit
    ): CurationResult {
        val memberId = memberProfileDto.memberId
            ?: throw IllegalArgumentException("존재하지 않는 회원입니다.")

        // 이미 관심사가 등록되어 있는지 체크
        require(!memberInterestService.hasInterests(memberId)) {
            "이미 초기 설정이 완료된 회원입니다."
        }

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
        val interestsWithPaths = interests.mapNotNull { dto ->
            dto.interestId?.let { id -> Pair(id, dto.directFullPath) }
        }
        val saveResult = memberInterestService.saveInterests(memberId, interestsWithPaths)
        val memberInterestIds = saveResult.savedIds
        onLog(CurationLogEvent("회원 관심사 설정 완료", memberInterestIds.toString()))
        delaySafe()

        /** STEP 06 미션 추천 */
        val missions = missionRecommendService.recommendByMemberInterestIds(memberId, memberInterestIds)


        /** MissionRecommendationResponse → JSON 문자열 */
        val missionsJson: String = objectMapper.writeValueAsString(missions)
        onLog( CurationLogEvent( step = "회원 미션 설정 완료", message = missionsJson ) )
        delaySafe()


        onLog(CurationLogEvent("초기 회원 설정 완료", ""))

        return CurationResult(memberInterestIds = memberInterestIds)
    }

    private suspend fun delaySafe() {
        delay(700)
    }
}