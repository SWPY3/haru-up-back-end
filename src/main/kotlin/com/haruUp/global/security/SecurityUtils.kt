package com.haruUp.global.security

import jakarta.annotation.PostConstruct
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component

@Component
class SecurityUtils {

    fun getCurrentMemberId(): Long? {
        val auth: Authentication? = SecurityContextHolder.getContext().authentication
        val principal = auth?.principal
        return if (principal is MemberPrincipal) {
            principal.id
        } else null
    }


    @PostConstruct
    fun enableSecurityContextPropagation() {
        SecurityContextHolder.setStrategyName(
            SecurityContextHolder.MODE_INHERITABLETHREADLOCAL
        )
    }

}