package com.haruUp.chat.repository

import com.haruUp.chat.domain.ChatState
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Repository
import java.util.concurrent.TimeUnit

@Repository
class ChatRedisRepository(
    @Qualifier("chatRedisTemplate")
    private val redisTemplate: RedisTemplate<String, ChatState>
) {

    companion object {
        /**
         * Redis key prefix
         *
         * 실제 Redis에는 * chat:state:{sessionId} * 형태로 저장된다.
         */
        private const val KEY_PREFIX = "chat:state:"

        /**
         * 대화 상태 유지 시간 (분 단위)
         *
         * 사용자가 일정 시간 동안 대화를 이어가지 않으면
         * Redis에서 자동으로 삭제되도록 한다.
         *
         * 예:
         * 30분 동안 응답이 없으면 대화 상태 만료
         */
        private const val TTL_MINUTES = 30L
    }

    /* sessionId로 ChatState 조회 */
     fun findBySessionId(sessionId: String): ChatState? {
        return redisTemplate.opsForValue().get(KEY_PREFIX + sessionId)
    }

    /* sessionId로 ChatState 저장 */
    fun saveChatState(sessionId: String, state: ChatState) {
        redisTemplate.opsForValue().set(
            KEY_PREFIX + sessionId,
            state,
            TTL_MINUTES,
            TimeUnit.MINUTES
        )
    }

    /* sessionId로 ChatState 삭제 */
    fun deleteBySessionId(sessionId: String) {
        val key = KEY_PREFIX + sessionId
        redisTemplate.delete(key)
    }

    fun generateKey(sessionId : String): String {
        return  KEY_PREFIX + sessionId
    }


}
