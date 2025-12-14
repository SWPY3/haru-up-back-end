package com.haruUp.member.controller

import com.haruUp.character.domain.dto.CharacterDto
import com.haruUp.global.common.ApiResponse
import com.haruUp.global.error.BusinessException
import com.haruUp.global.error.ErrorCode
import com.haruUp.global.security.MemberPrincipal
import com.haruUp.member.application.useCase.MemberProfileUseCase
import com.haruUp.member.domain.MemberProfile
import com.haruUp.member.domain.dto.MemberProfileDto
import com.haruUp.member.domain.type.MemberGender
import com.haruUp.member.infrastructure.MemberProfileRepository
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.apply

@RestController
@RequestMapping("/api/member/profile")
@Tag(name = "Member", description = "회원 프로필 관리 API")
class MemberProfileController(
    private val memberProfileUseCase: MemberProfileUseCase,
    private val memberProfileRepository: MemberProfileRepository,
) {

    // =====================
    // 프로필
    // =====================

    @Operation(summary = "프로필, 캐릭터 CREATE",
        description = """
            캐릭터 선택후 프로필 입력완료 시점에 호출하기
        """
    )
    @PostMapping("/default_profile")
    fun createDefaultProfile(
        @AuthenticationPrincipal principal : MemberPrincipal,
        @RequestBody characterDto : CharacterDto
    ) : ApiResponse<String>{

        var characterId: Long? = characterDto.id
        if (characterId == null) {
            throw BusinessException(ErrorCode.NOT_FOUND, "캐릭터를 찾을 수 없습니다.")
        }

        memberProfileUseCase.createDefaulProfile(principal.id, characterId)

        return ApiResponse.success( "OK")
    }

    // 닉네임 중복 체크
    @PostMapping("/nickName_duplicate_check")
    fun nickNameDuplicationCheck(
        @AuthenticationPrincipal principal: MemberPrincipal,
        @RequestBody nickName : String
    ) : ApiResponse<Boolean>{

        val nickNameDuplicationCheck = memberProfileUseCase.nickNameDuplicationCheck(nickName)

        return ApiResponse.success(nickNameDuplicationCheck)
    }



    // 프로필 저장 ( 큐레이션용 )

    @PostMapping("/curation_profile_save")
    fun curationProfileSave(
        @AuthenticationPrincipal principal: MemberPrincipal,
        @RequestBody addMemberProfileDto: AddMemberProfileDto
    ): ApiResponse<String> {

        val birthDt: LocalDateTime? =
            addMemberProfileDto.birthDate?.let {
                // 날짜만 받는 경우 (권장)
                LocalDate.parse(it, DateTimeFormatter.ISO_DATE).atStartOfDay()
            }

        memberProfileUseCase.addProfile(
            principal.id,
            MemberProfileDto().apply {
                this.nickname = addMemberProfileDto.nickName
                this.birthDt = birthDt
                this.gender = addMemberProfileDto.gender
            }
        )

        return ApiResponse.success("OK")
    }



    @Operation(summary = "회원 프로필 조회")
    @GetMapping("/profile")
    fun getProfile( @AuthenticationPrincipal principal: MemberPrincipal ): ApiResponse<MemberProfileDto> {
        val memberId = principal.id
        val profile = memberProfileUseCase.getMyProfile(memberId)
        return ApiResponse.success(profile)
    }


    @Operation(summary = "회원 프로필 수정")
    @PutMapping("/profile")
    fun updateProfile( @AuthenticationPrincipal principal: MemberPrincipal, @RequestBody request: MemberProfileDto ): ApiResponse<MemberProfileDto> {
        val memberId = principal.id
        val updatedProfile = memberProfileUseCase.updateMyProfile(memberId, request)
        return ApiResponse.success(updatedProfile)
    }



    @RequestMapping("/member_job_update")
    fun memberJobUpdate(
        @AuthenticationPrincipal principal: MemberPrincipal,
        @RequestBody memberJob : MemberJob) : MemberProfileDto{
        val memberId = principal.id
        return memberProfileUseCase.memberJobUpdate(memberId, memberJob.jobId!!)

    }

    @RequestMapping("/member_job_detaile_update")
    fun memberJobDetailUpdate(
        @AuthenticationPrincipal principal: MemberPrincipal,
        @RequestBody memberJob : MemberJob) : MemberProfileDto{
        val memberId = principal.id
        return memberProfileUseCase.memberJobDetailUpdate(memberId, memberJob.jobDetailId!!)
    }


}

data class AddMemberProfileDto(
    val nickName: String?,
    val birthDate :  String?,
    val gender : MemberGender?
)

data class MemberJob(
    val jobId : Long?,
    val jobDetailId : Long?
)
