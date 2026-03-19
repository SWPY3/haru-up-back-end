package com.haruUp.member.application.useCase

import com.haruUp.auth.application.RefreshTokenService
import com.haruUp.global.error.BusinessException
import com.haruUp.global.error.ErrorCode
import com.haruUp.member.application.service.MemberService
import com.haruUp.member.application.service.MemberSettingService
import com.haruUp.interest.service.MemberInterestService
import com.haruUp.mission.application.MemberCustomMissionService
import com.haruUp.mission.application.MemberMissionService
import com.haruUp.member.application.service.MemberValidator
import com.haruUp.member.domain.Member
import com.haruUp.member.domain.dto.HomeMemberInfoDto
import com.haruUp.member.domain.dto.MemberDto
import com.haruUp.member.domain.type.LoginType
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.io.ByteArrayOutputStream

@Component
class MemberAccountUseCase(
    private val memberService: MemberService,
    private val memberSettingService: MemberSettingService,
    private val passwordEncoder: PasswordEncoder,
    private val memberValidator: MemberValidator,
    private val refreshTokenService: RefreshTokenService,
    private val memberInterestService: MemberInterestService,
    private val memberMissionService: MemberMissionService,
    private val memberCustomMissionService: MemberCustomMissionService
) {

    /** 회원 ID로 사용자 정보를 조회한다. */
    @Transactional(readOnly = true)
    fun findMemberById(memberId: Long): MemberDto {
        return getMemberOrThrow(memberId).toDto()
    }

    /** COMMON 계정 기준 이메일 중복 여부를 조회한다. */
    @Transactional(readOnly = true)
    fun isEmailDuplicate(email: String): Boolean =
        memberService.findByEmailAndLoginType(email, LoginType.COMMON) != null

    /** 회원 이메일을 변경한다. */
    @Transactional
    fun changeEmail(memberId: Long, newEmail: String): MemberDto {
        // 1) 회원 조회
        val member = getMemberOrThrow(memberId)

        // 2) 이메일 중복 검증
        memberValidator.validateEmailDuplication(newEmail)

        // 3) 이메일 변경
        member.email = newEmail
        return memberService.updateMember(member)
    }

    /**
     * 🔑 비밀번호 변경 (COMMON 계정만)
     * - 기존 비밀번호 검증 후 새 비밀번호로 교체
     */
    @Transactional
    fun changePassword(memberId: Long, currentPassword: String, newPassword: String) {
        // 1) 회원 조회
        val member = getMemberOrThrow(memberId)

        // 2) COMMON 계정만 비밀번호 변경 허용
        if (member.loginType != LoginType.COMMON) {
            throw BusinessException(
                ErrorCode.INVALID_INPUT, "SNS 로그인 계정은 비밀번호를 변경할 수 없습니다."
            )
        }

        // 3) 기존 비밀번호 검증
        val encoded = member.password ?: throw BusinessException(ErrorCode.INVALID_STATE, "저장된 비밀번호가 없습니다.")

        if (!passwordEncoder.matches(currentPassword, encoded)) {
            throw BusinessException(ErrorCode.INVALID_CREDENTIALS, "기존 비밀번호가 일치하지 않습니다.")
        }

        // 4) 새 비밀번호 검증 (간단 버전 – 필요하면 Validator로 분리)
        if (newPassword.length < 8) {
            throw BusinessException(ErrorCode.INVALID_INPUT, "새 비밀번호는 8자리 이상이어야 합니다.")
        }

        // 5) 새 비밀번호 저장
        val newEncoded = passwordEncoder.encode(newPassword)
        member.password = newEncoded

        memberService.updateMember(member)  // 반환값은 굳이 안 써도 됨
    }

    /** 회원 탈퇴 처리(관련 데이터 soft/hard delete 포함)를 수행한다. */
    @Transactional
    fun withdraw(memberId: Long, passwordForCheck: String?) {
        // 1) 회원 조회
        val member = getMemberOrThrow(memberId)

        // 2) COMMON 계정은 비밀번호 검증
        if (member.loginType == LoginType.COMMON) {
            val raw = passwordForCheck ?: throw BusinessException(ErrorCode.INVALID_INPUT, "비밀번호가 필요합니다.")

            val encoded = member.password ?: throw BusinessException(ErrorCode.INVALID_STATE, "저장된 비밀번호가 없습니다.")

            if (!passwordEncoder.matches(raw, encoded)) {
                throw BusinessException(ErrorCode.INVALID_CREDENTIALS, "비밀번호가 일치하지 않습니다.")
            }
        }

        // 3) RefreshToken은 어차피 수명 짧고, 보안상 확실히 제거하는 게 좋아서 hard delete 유지
        refreshTokenService.deleteAllByMemberId(memberId)

        // 4) MemberSetting soft delete
        val byMemberId = memberSettingService.getByMemberId(memberId)
        memberSettingService.softDelete(byMemberId.toEntity())

        // 5) Member soft delete
        memberService.softDelete(member)

        memberInterestService.deleteMemberInterestsByMemberId(memberId)

        memberMissionService.deleteMemberMissionsByMemberId(memberId)

        memberCustomMissionService.deleteAllByMemberId(memberId)
    }

    /** 홈 화면용 회원 정보와 관심사 목록을 함께 반환한다. */
    fun homeMemberInfo(memberId: Long): List<HomeMemberInfoDto> {
        // 회원 기본 정보
        val homeMemberInfo = memberService.homeMemberInfo(memberId)

        // 관심사 fullPath 리스트
        val interests = memberInterestService.selectMemberInterestsByMemberId(memberId)

        return homeMemberInfo.map { dto ->
            dto.copy(interests = interests)
        }
    }

    /** 회원 통계 엑셀 파일(ByteArray)을 생성한다. */
    @Transactional(readOnly = true)
    fun createMemberStatisticsExcel(): ByteArray {
        val data = memberService.memberStatisticsList()

        val headers = listOf("SNS ID", "이름", "레벨", "캐릭터 ID", "가입일")
        return XSSFWorkbook().use { workbook ->
            val sheet = workbook.createSheet("Member Statistics")

            // 1️⃣ Header
            val headerRow = sheet.createRow(0)
            headers.forEachIndexed { index, title ->
                headerRow.createCell(index).setCellValue(title)
            }

            // 2️⃣ Data
            data.forEachIndexed { rowIdx, dto ->
                val row = sheet.createRow(rowIdx + 1)
                row.createCell(0).setCellValue(dto.snsId)
                row.createCell(1).setCellValue(dto.name)
                row.createCell(2).setCellValue(dto.levelNumber.toDouble())
                row.createCell(3).setCellValue(dto.characterId.toDouble())
                row.createCell(4).setCellValue(dto.createdAt.toString())
            }

            // 3️⃣ 컬럼 자동 너비
            headers.indices.forEach { sheet.autoSizeColumn(it) }

            // 4️⃣ ByteArray 변환
            ByteArrayOutputStream().use { outputStream ->
                workbook.write(outputStream)
                outputStream.toByteArray()
            }
        }
    }

    /** 회원 ID로 엔티티를 조회하고 없으면 예외를 발생시킨다. */
    private fun getMemberOrThrow(memberId: Long): Member =
        memberService.getFindMemberId(memberId).orElseThrow {
            BusinessException(ErrorCode.MEMBER_NOT_FOUND, "회원 정보를 찾을 수 없습니다.")
        }

}
