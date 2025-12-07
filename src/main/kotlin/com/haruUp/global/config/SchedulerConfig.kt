package com.haruUp.global.config

import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling

/**
 * 스프링 스케줄러 설정
 *
 * @Scheduled 어노테이션을 사용하기 위해 필요
 */
@Configuration
@EnableScheduling
class SchedulerConfig
