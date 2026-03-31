package com.haruUp.member.application.service

import com.haruUp.member.domain.dto.HomeMemberInfoDto
import com.haruUp.member.domain.dto.MemberStatisticsDto
import com.haruUp.member.infrastructure.MemberRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExtendWith(MockitoExtension::class)
/**
 * MemberService가 Repository 결과를 변형 없이 반환하는지 검증한다.
 * (서비스 계층 위임 계약 테스트)
 */
class MemberServiceTest {

    @Mock
    private lateinit var memberRepository: MemberRepository

    @InjectMocks
    private lateinit var memberService: MemberService

    // 목적: 홈 정보 조회 API에서 서비스가 repository 응답을 그대로 전달하는지 확인
    @Test
    fun `homeMemberInfo는 repository 결과를 그대로 반환한다`() {
        // given
        val memberId = 101L
        val expected = listOf(
            HomeMemberInfoDto(
                characterId = 1L,
                totalExp = 120L,
                currentExp = 20L,
                maxExp = 500,
                levelNumber = 3,
                nickname = "tester"
            )
        )
        whenever(memberRepository.homeMemberInfo(memberId)).thenReturn(expected)

        // when
        val result = memberService.homeMemberInfo(memberId)

        // then
        assertEquals(expected, result)
        verify(memberRepository).homeMemberInfo(memberId)
    }

    // 목적: 통계 조회 API에서 서비스가 repository 응답을 그대로 전달하는지 확인
    @Test
    fun `memberStatisticsList는 repository 결과를 그대로 반환한다`() {
        // given
        val expected = listOf(
            MemberStatisticsDto(
                snsId = "sns-1",
                name = "member-1",
                levelNumber = 2,
                characterId = 3L,
                createdAt = "2026-01-10T00:00:00"
            )
        )
        whenever(memberRepository.memberStatisticsList()).thenReturn(expected)

        // when
        val result = memberService.memberStatisticsList()

        // then
        assertEquals(expected, result)
        verify(memberRepository).memberStatisticsList()
    }
}
