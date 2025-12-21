package com.haruUp.member.controller

import com.haruUp.global.security.MemberPrincipal
import com.haruUp.interest.dto.InterestPathDto
import com.haruUp.member.application.useCase.MemberCurationUseCase
import com.haruUp.member.domain.dto.MemberProfileDto
import com.haruUp.member.domain.type.MemberGender
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Schema
import kotlinx.coroutines.runBlocking
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.time.LocalDate
import java.util.concurrent.Executor

@RestController
@RequestMapping("/api/member/curation")
class MemberCurationController(
    private val memberCurationUseCase: MemberCurationUseCase,
    private val sseExecutor: Executor,
) {


    @PostMapping("/initial", produces = ["text/event-stream"])
    @Operation(
        summary = "초기 회원 큐레이션 실행",
        description = """
        회원 가입 직후 실행되는 초기 큐레이션 API입니다.
        - 캐릭터 생성
        - 프로필 설정
        - 관심사 저장
        - 미션 추천
        SSE 방식으로 진행 로그를 스트리밍합니다.
    """
    )
    fun runInitialCuration(
    @AuthenticationPrincipal principal: MemberPrincipal,
    @RequestBody curationDto: CurationRequest
    ): SseEmitter {

        val emitter = SseEmitter(0L)

        sseExecutor.execute {
            runBlocking {
                try {
                    emitter.send(SseEmitter.event().name("connected").data("큐레이션 시작"))

                    val birthDt = LocalDate
                        .parse(curationDto.birthDt)
                        .atStartOfDay()

                    val profileDto = MemberProfileDto().apply {
                        memberId = principal.id
                        nickname = curationDto.nickname
                        this.birthDt = birthDt
                        gender = curationDto.gender
                        jobId = curationDto.jobId
                        jobDetailId = curationDto.jobDetailId
                    }

                    memberCurationUseCase.runInitialCuration(
                        characterId = curationDto.characterId,
                        memberProfileDto = profileDto,
                        interests = curationDto.interestPathList
                    ) {
                        emitter.send(
                            SseEmitter.event()
                                .name("curation-log")
                                .data(it)
                        )
                    }

                    emitter.send(SseEmitter.event().name("done").data("완료"))
                    emitter.complete()

                } catch (e: Exception) {
                    emitter.completeWithError(e)
                }
            }
        }

        return emitter
    }



    @Schema(description = "초기 회원 큐레이션 요청")
    data class CurationRequest(

        @Schema(
            description = "선택한 캐릭터 ID",
            example = "1"
        )
        val characterId: Long,

        @Schema(
            description = "닉네임",
            example = "테스트"
        )
        val nickname: String,

        @Schema(
            description = "생년월일 (yyyy-MM-dd)",
            example = "1995-07-30"
        )
        val birthDt: String,

        @Schema(
            description = "성별",
            example = "MALE",
            allowableValues = ["MALE", "FEMALE"]
        )
        val gender: MemberGender,

        @Schema(
            description = "직업 ID",
            example = "1",
            nullable = true
        )
        val jobId: Long?,

        @Schema(
            description = "직업 상세 ID",
            example = "1",
            nullable = true
        )
        val jobDetailId: Long?,

        @Schema(description = "관심사 경로 목록")
        val interestPathList: List<InterestPathDto>
    )
    }
