package com.haruUp.member.infrastructure

import com.haruUp.member.domain.MemberAttendance
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
interface MemberAttendanceRepository : JpaRepository<MemberAttendance, Long> {

    fun existsByMemberIdAndAttendanceDate(memberId: Long, attendanceDate: LocalDate): Boolean

    @Query("""
        SELECT a.attendanceDate
        FROM MemberAttendance a
        WHERE a.memberId = :memberId
          AND a.attendanceDate >= :startDate
          AND a.attendanceDate <= :endDate
          AND a.deleted = false
    """)
    fun findAttendanceDatesByMemberIdAndDateRange(
        memberId: Long,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<LocalDate>
}
