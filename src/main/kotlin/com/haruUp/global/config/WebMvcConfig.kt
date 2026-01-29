package com.haruUp.global.config

import com.haruUp.global.interceptor.AttendanceInterceptor
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebMvcConfig(
    private val attendanceInterceptor: AttendanceInterceptor
) : WebMvcConfigurer {

    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(attendanceInterceptor)
            .addPathPatterns("/api/**")
            .excludePathPatterns(
                "/api/member/auth/**",
                "/api/health/**"
            )
    }
}
