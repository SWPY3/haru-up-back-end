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
@Tag(
    name = "Member",
    description = "회원 프로필(캐릭터, 기본 정보, 직업 등) 관리 API"
)
class MemberProfileController(
    private val memberProfileUseCase: MemberProfileUseCase,
    private val memberProfileRepository: MemberProfileRepository,
) {

//    // =====================
//    // 프로필
//    // =====================
//
//    @Operation(
//        summary = "기본 프로필 및 캐릭터 생성",
//        description = """
//            캐릭터 선택 후, 회원의 기본 프로필을 최초로 생성하는 API입니다.
//
//            - 선택한 캐릭터 ID를 회원 프로필과 연결합니다.
//            - 회원당 1회만 호출되는 것을 전제로 합니다.
//            - 이미 프로필이 존재하는 경우 예외가 발생할 수 있습니다.
//
//            📌 사용 시점
//            - 회원 가입 완료
//            - 캐릭터 선택 완료 직후
//        """
//    )
//    @PostMapping("/default_profile")
//    fun createDefaultProfile(
//        @AuthenticationPrincipal principal: MemberPrincipal,
//        @RequestBody characterDto: CharacterDto
//    ): ApiResponse<String> {
//
//        val characterId = characterDto.id
//            ?: throw BusinessException(ErrorCode.NOT_FOUND, "캐릭터를 찾을 수 없습니다.")
//
//        memberProfileUseCase.createDefaulProfile(principal.id, characterId)
//
//        return ApiResponse.success("OK")
//    }

    // =====================
    // 닉네임
    // =====================

    @Operation(
        summary = "닉네임 중복 검사",
        description = """
            입력한 닉네임이 이미 사용 중인지 확인하는 API입니다.
            
            - true  : 이미 사용 중인 닉네임
            - false : 사용 가능한 닉네임
            
            📌 회원 가입 또는 프로필 설정 과정에서 사용됩니다.
        """
    )
    @PostMapping("/nickName_duplicate_check")
    fun nickNameDuplicationCheck(
        @AuthenticationPrincipal principal: MemberPrincipal,
        @RequestBody request : NickNameCheckRequest
    ): ApiResponse<Any> {

        val result = memberProfileUseCase.nickNameDuplicationCheck(request.nickName)

        println("result : $result");

        return if(result){
            ApiResponse(false, "중복된 닉네임 입니다", "닉네임 중복")
        }else{
            ApiResponse.success("닉네임 사용가능")
        }
    }

    // =====================
    // 큐레이션 프로필 저장
    // =====================

    @Operation(
        summary = "큐레이션용 프로필 정보 저장",
        description = """
            추천/큐레이션 기능을 위해 필요한 최소한의 프로필 정보를 저장합니다.
            
            저장 항목:
            - 닉네임
            - 생년월일 (yyyyMMdd 형식)
            - 성별
            
            📌 특징
            - 생년월일은 날짜 기준으로만 저장되며 시간은 00:00:00으로 처리됩니다.
            - 필수 정보가 아닌 경우 null 값을 허용합니다.
        """
    )
    @PostMapping("/curation_profile_save")
    fun curationProfileSave(
        @AuthenticationPrincipal principal: MemberPrincipal,
        @RequestBody addMemberProfileDto: AddMemberProfileDto
    ): ApiResponse<String> {

        val birthDt: LocalDateTime? =
            addMemberProfileDto.birthDate?.let {
                LocalDate.parse(it, DateTimeFormatter.ofPattern("yyyyMMdd")).atStartOfDay()
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

    // =====================
    // 프로필 조회 / 수정
    // =====================

    @Operation(
        summary = "회원 프로필 조회",
        description = """
            현재 로그인한 회원의 프로필 정보를 조회합니다.
            
            반환 정보 예시:
            - 닉네임
            - 생년월일
            - 성별
            - 캐릭터 정보
            - 직업 및 직업 상세 정보
        """
    )
    @GetMapping("/profile")
    fun getProfile(
        @AuthenticationPrincipal principal: MemberPrincipal
    ): ApiResponse<MemberProfileDto> {
        val profile = memberProfileUseCase.getMyProfile(principal.id)
        return ApiResponse.success(profile)
    }

    @Operation(
        summary = "회원 프로필 수정",
        description = """
            회원의 프로필 정보를 수정하는 API입니다.
            
            - 닉네임, 생년월일, 성별 등을 수정할 수 있습니다.
            - 전달되지 않은 필드는 기존 값이 유지됩니다.
            
            📌 프로필 수정 화면에서 사용됩니다.
        """
    )
    @PutMapping("/profile")
    fun updateProfile(
        @AuthenticationPrincipal principal: MemberPrincipal,
        @RequestBody request: MemberProfileDto
    ): ApiResponse<MemberProfileDto> {
        val updatedProfile =
            memberProfileUseCase.updateMyProfile(principal.id, request)
        return ApiResponse.success(updatedProfile)
    }

    // =====================
    // 직업
    // =====================

    @Operation(
        summary = "회원 직업 설정",
        description = """
            회원의 직업(대분류)을 설정하는 API입니다.
            
            - 기존 직업 정보가 존재하는 경우 새로운 값으로 갱신됩니다.
        """
    )
    @PostMapping("/member_job_update")
    fun memberJobUpdate(
        @AuthenticationPrincipal principal: MemberPrincipal,
        @RequestBody memberJob: MemberJob
    ): MemberProfileDto {
        return memberProfileUseCase.memberJobUpdate(
            principal.id,
            memberJob.jobId!!
        )
    }

    @Operation(
        summary = "회원 직업 상세 설정",
        description = """
            회원의 직업 상세 정보를 설정하는 API입니다.
            
            - 직업 대분류 설정 이후 호출됩니다.
            - 선택한 직업에 속한 상세 직군을 설정합니다.
        """
    )
    @PostMapping("/member_job_detaile_update")
    fun memberJobDetailUpdate(
        @AuthenticationPrincipal principal: MemberPrincipal,
        @RequestBody memberJob: MemberJob
    ): MemberProfileDto {
        return memberProfileUseCase.memberJobDetailUpdate(
            principal.id,
            memberJob.jobDetailId!!
        )
    }


    data class AddMemberProfileDto(
        val nickName: String?,
        val birthDate: String?,
        val gender: MemberGender?
    )

    data class MemberJob(
        val jobId: Long?,
        val jobDetailId: Long?
    )

    data class NickNameCheckRequest(
        val nickName: String
    )
}
