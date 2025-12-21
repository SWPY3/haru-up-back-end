package com.haruUp.global.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.security.concurrent.DelegatingSecurityContextExecutor
import java.util.concurrent.Executor

@Configuration
class SseExecutorConfig {


    /**
     * SSE 전용 Executor
     *
     * - SSE는 네트워크 I/O가 포함된 작업이므로
     *   commonPool이 아닌 별도의 스레드 풀을 사용해야 한다.
     */
    @Bean
    fun sseExecutor(): Executor {
        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = 1
        executor.maxPoolSize = 5
        executor.setThreadNamePrefix("sse-")
        executor.initialize()

        return DelegatingSecurityContextExecutor(executor)
    }
}
