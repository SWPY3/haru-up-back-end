package com.haruUp.member.application.service

import com.haruUp.member.domain.type.LoginType
import com.haruUp.member.domain.Member
import com.haruUp.member.domain.dto.HomeMemberInfoDto
import com.haruUp.member.domain.dto.MemberDto
import com.haruUp.member.domain.dto.MemberStatisticsDto
import com.haruUp.member.infrastructure.MemberRepository
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service
import java.util.Optional

@Service
class MemberService(
    private val memberRepository: MemberRepository
) {

    /** 회원 정보를 저장하고 DTO로 반환한다. */
    @Transactional
    fun addMember(member: Member): MemberDto = memberRepository.save(member).toDto()

    /** 기존 회원 정보를 갱신하고 DTO로 반환한다. */
    @Transactional
    fun updateMember(member: Member): MemberDto = memberRepository.save(member).toDto()

    /** 전달받은 회원 엔티티의 ID로 회원을 조회한다. */
    @Transactional
    fun findMember(member: Member): MemberDto? =
        member.id
            ?.let { memberRepository.findById(it) }
            ?.orElse(null)
            ?.toDto()

    /** 회원 ID 기준으로 회원 데이터를 삭제한다. */
    @Transactional
    fun deleteMember(member: Member) {
        member.id?.let { memberRepository.removeMemberById(it) }
    }

    /** 회원 ID로 엔티티를 조회한다. */
    @Transactional
    fun getFindMemberId(id: Long): Optional<Member> = memberRepository.findById(id)

    /** 이메일/비밀번호 기반으로 활성 회원을 조회한다. */
    @Transactional
    fun getByEmailAndPassword(email: String?, password: String?): MemberDto? =
        memberRepository.findByEmailAndPasswordAndDeletedFalse(email, password)?.toDto()

    /** 로그인 타입과 이메일이 일치하는 활성 회원을 조회한다. */
    @Transactional
    fun findByEmailAndLoginType(email: String, common: LoginType): MemberDto? =
        memberRepository.findByEmailAndLoginTypeAndDeletedFalse(email, common)?.toDto()

    /** 로그인 타입과 SNS ID가 일치하는 활성 회원을 조회한다. */
    @Transactional
    fun findByLoginTypeAndSnsId(loginType: LoginType?, snsId: String): MemberDto? =
        memberRepository.findByLoginTypeAndSnsIdAndDeletedFalse(loginType, snsId)?.toDto()

    /** 회원을 soft delete 상태로 변경한다. */
    fun softDelete(member: Member) {
        val saved = member.apply { deleted = true }
        memberRepository.save(saved)
    }

    /** 홈 화면용 회원 정보를 조회한다. */
    fun homeMemberInfo(memberId: Long): List<HomeMemberInfoDto> = memberRepository.homeMemberInfo(memberId)

    /** 회원 통계 목록을 조회한다. */
    fun memberStatisticsList(): List<MemberStatisticsDto> = memberRepository.memberStatisticsList()
}
