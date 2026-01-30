package com.haruUp.member.domain

import com.haruUp.global.common.BaseEntity
import jakarta.persistence.*
import java.time.LocalDate

@Entity
@Table(
    name = "member_attendance",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_member_attendance_member_date",
            columnNames = ["member_id", "attendance_date"]
        )
    ],
    indexes = [
        Index(name = "idx_member_attendance_member_id", columnList = "member_id"),
        Index(name = "idx_member_attendance_date", columnList = "attendance_date")
    ]
)
class MemberAttendance(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "member_id", nullable = false)
    val memberId: Long,

    @Column(name = "attendance_date", nullable = false)
    val attendanceDate: LocalDate
) : BaseEntity()
