package com.haruUp.member.application.service

import com.haruUp.member.domain.MemberAttendance
import com.haruUp.member.infrastructure.MemberAttendanceRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
class MemberAttendanceService(
    private val memberAttendanceRepository: MemberAttendanceRepository
) {

    /**
     * 오늘 출석 체크
     * - 이미 오늘 출석 기록이 있으면 무시
     * - 없으면 새로 생성
     */
    @Transactional
    fun checkAttendance(memberId: Long): Boolean {
        val today = LocalDate.now()

        if (memberAttendanceRepository.existsByMemberIdAndAttendanceDate(memberId, today)) {
            return false
        }

        val attendance = MemberAttendance(
            memberId = memberId,
            attendanceDate = today
        )
        memberAttendanceRepository.save(attendance)
        return true
    }

    /**
     * 특정 기간의 출석일 목록 조회
     */
    fun getAttendanceDates(memberId: Long, startDate: LocalDate, endDate: LocalDate): List<LocalDate> {
        return memberAttendanceRepository.findAttendanceDatesByMemberIdAndDateRange(
            memberId, startDate, endDate
        )
    }
}
