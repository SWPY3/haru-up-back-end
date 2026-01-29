package com.haruUp.global.interceptor

import com.haruUp.global.security.MemberPrincipal
import com.haruUp.member.application.service.MemberAttendanceService
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor

@Component
class AttendanceInterceptor(
    private val memberAttendanceService: MemberAttendanceService
) : HandlerInterceptor {

    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any
    ): Boolean {
        val authentication = SecurityContextHolder.getContext().authentication

        if (authentication != null && authentication.principal is MemberPrincipal) {
            val principal = authentication.principal as MemberPrincipal
            memberAttendanceService.checkAttendance(principal.id)
        }

        return true
    }
}
