package com.haruUp.mission.infrastructure

import com.haruUp.mission.domain.MemberMissionEntity
import com.haruUp.mission.domain.MissionStatus
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import java.time.LocalDate


@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class MemberMissionRepositoryTest(
    @Autowired private val memberMissionRepository: MemberMissionRepository
) {

    @BeforeEach
    fun setUp() {
        memberMissionRepository.deleteAll()
    }

    @Test
    fun `한 달 동안 날짜별 완료 미션 개수를 조회한다`() {
        // --------------------------------
        // given
        // --------------------------------
        val memberId = 1L

        memberMissionRepository.saveAll(
            listOf(
                // 1월 1일 → 3개 완료
                completed(memberId, "미션1", 2025, 1, 1),
                completed(memberId, "미션2", 2025, 1, 1),
                completed(memberId, "미션3", 2025, 1, 1),

                // 1월 2일 → 1개 완료
                completed(memberId, "미션4", 2025, 1, 2),

                // 1월 3일 → 완료 0개 (ACTIVE)
                active(memberId, "미션5", 2025, 1, 3),

                // 1월 5일 → 2개 완료 (중간 날짜 비어 있음)
                completed(memberId, "미션6", 2025, 1, 5),
                completed(memberId, "미션7", 2025, 1, 5),

                // 다른 사용자 → 제외
                completed(999L, "다른유저미션", 2025, 1, 1)
            )
        )

        // --------------------------------
        // when
        // --------------------------------
        val result = memberMissionRepository.findDailyCompletedMissionCount(
            memberId = memberId,
            targetStartDate = LocalDate.of(2025, 1, 1),
            targetEndDate = LocalDate.of(2025, 1, 31),
        )

        // --------------------------------
        // then (print 먼저)
        // --------------------------------
        println("===== 날짜별 완료 미션 개수 =====")
        result.forEach {
            println("date=${it.targetDate}, completed=${it.completedCount}")
        }
        println("================================")

        assertEquals(3, result.size)

        assertEquals(LocalDate.of(2025, 1, 1), result[0].targetDate)
        assertEquals(3L, result[0].completedCount)

        assertEquals(LocalDate.of(2025, 1, 2), result[1].targetDate)
        assertEquals(1L, result[1].completedCount)

        assertEquals(LocalDate.of(2025, 1, 5), result[2].targetDate)
        assertEquals(2L, result[2].completedCount)
    }

    // -------------------------------
    // 테스트용 헬퍼 메서드
    // -------------------------------
    private fun completed(
        memberId: Long,
        content: String,
        y: Int,
        m: Int,
        d: Int
    ) = MemberMissionEntity(
        memberId = memberId,
        memberInterestId = 1L,
        missionContent = content,
        expEarned = 10,
        missionStatus = MissionStatus.COMPLETED,
        targetDate = LocalDate.of(y, m, d),
        isSelected = true
    )

    private fun active(
        memberId: Long,
        content: String,
        y: Int,
        m: Int,
        d: Int
    ) = MemberMissionEntity(
        memberId = memberId,
        memberInterestId = 1L,
        missionContent = content,
        expEarned = 10,
        missionStatus = MissionStatus.ACTIVE,
        targetDate = LocalDate.of(y, m, d),
        isSelected = true
    )
}