package com.haruUp.mission.infrastructure

import com.haruUp.mission.domain.AiMissionResult
import org.springframework.aot.hint.TypeReference.listOf
import org.springframework.stereotype.Component


@Component
class MissionAiClient {

    /**
     * 유저 상태를 대표하는 임베딩 생성
     */
    fun createUserEmbedding(memberId: Long): String {
        // 1️⃣ 멤버 요약 텍스트 생성 (임시)
        val memberProfileText = """
            User prefers simple daily missions.
            Interested in self-improvement and healthy habits.
            Often completes missions consistently.
        """.trimIndent()

        // 2️⃣ 임베딩 생성 (Mock)
        val dummyVector = List(1024) { Math.random().toFloat() }

        return vectorToString(dummyVector)
    }

    /**
     * (임시) 미션 추천 – 지금 구조에선 거의 안 써도 됨
     */
    fun recommend(memberId: Long): List<AiMissionResult> {
        return listOf(
            AiMissionResult(1L, 0.91, "최근 활동량이 꾸준해요"),
            AiMissionResult(2L, 0.83, "집중력이 필요한 미션을 잘 완료했어요")
        )
    }

    private fun vectorToString(vector: List<Float>): String {
        return vector.joinToString(
            separator = ",",
            prefix = "[",
            postfix = "]"
        )
    }
}