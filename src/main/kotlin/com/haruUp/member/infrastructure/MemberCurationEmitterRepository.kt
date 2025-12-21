package com.haruUp.member.infrastructure

import org.springframework.stereotype.Component
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.concurrent.ConcurrentHashMap

@Component
class MemberCurationEmitterRepository {

    private val emitters = ConcurrentHashMap<Long, SseEmitter>()

    fun save(memberId: Long, emitter: SseEmitter) {
        emitters[memberId] = emitter

        emitter.onCompletion {
            emitters.remove(memberId)
        }

        emitter.onTimeout {
            emitters.remove(memberId)
        }

        emitter.onError {
            emitters.remove(memberId)
        }
    }

    fun send(memberId: Long, eventName: String, data: Any) {
        val emitter = emitters[memberId] ?: return

        try {
            emitter.send(
                SseEmitter.event()
                    .name(eventName)
                    .data(data)
            )
        } catch (e: Exception) {
            emitters.remove(memberId)
        }
    }

    fun complete(memberId: Long) {
        emitters[memberId]?.complete()
        emitters.remove(memberId)
    }
}