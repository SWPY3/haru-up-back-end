package com.haruUp.global.security

import com.haruUp.member.application.service.MemberService
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.web.filter.OncePerRequestFilter

/**
 * 매 요청마다 한 번씩 실행되는 JWT 인증 필터.
 *
 * 역할:
 *  - HTTP 헤더에서 JWT 추출
 *  - 토큰 유효성 검증
 *  - 토큰에서 memberId 추출 후 DB에서 회원 조회
 *  - MemberPrincipal 생성 후 SecurityContext 에 Authentication 설정
 *
 * 이 필터는 SecurityConfig에서 Bean으로 등록해서 사용한다.
 */
class JwtAuthenticationFilter(
    private val jwtTokenProvider: JwtTokenProvider,
    private val memberService: MemberService,
) : OncePerRequestFilter() {

    /**
     * 실제 필터 로직이 들어가는 메서드
     *
     * @param request  들어온 HTTP 요청
     * @param response 응답
     * @param filterChain 다음 필터로 넘기기 위한 체인
     */
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {

        // 1) 이미 SecurityContext에 인증 정보가 있는 경우
        //    -> 이 필터가 다시 인증할 필요 없음 (다음 필터로 바로 넘김)
        //
        // 🔴 기존 코드에선 == null 일 때 그냥 통과해버려서,
        //    "인증이 안 되어 있는" 경우에 인증을 시도하지 않는 버그가 있었음.
        //    그래서 != null 로 바꿔야 정상 동작.
        if (SecurityContextHolder.getContext().authentication != null) {
            filterChain.doFilter(request, response)
            return
        }

        // 2) 요청 헤더에서 JWT 토큰 추출 (Authorization / jwt-token)
        val token = resolveToken(request)

        // 3) 토큰이 있고, 서명 & 만료 시간 등 유효성이 검증되면
        if (token != null && jwtTokenProvider.validateToken(token)) {
            // 3-1) 토큰에서 memberId 추출
            val memberId = jwtTokenProvider.getMemberIdFromToken(token)

            // 3-2) DB에서 회원 정보 조회
            val memberOpt = memberService.getFindMemberId(memberId)
            if (memberOpt.isPresent) {
                val member = memberOpt.get()

                // 3-3) Spring Security용 Principal 객체 생성
                //      - 인증된 유저의 id, email, name 등 보안 관련 정보 담는 역할
                val principal = MemberPrincipal(
                    id = requireNotNull(member.id),
                    email = member.email ?: "",
                    name = member.name ?: ""
                )

                // 3-4) UsernamePasswordAuthenticationToken 생성
                //      - principal: 인증된 사용자 정보
                //      - credentials: 비밀번호 등 (JWT 기반이라 null)
                //      - authorities: 권한 목록 (MemberPrincipal이 UserDetails 구현했다고 가정)
                val auth = UsernamePasswordAuthenticationToken(
                    principal,
                    null,
                    principal.authorities
                )

                // 3-5) 현재 요청(request)에 대한 세부 정보(IP, 세션 등)도 Authentication에 세팅
                auth.details = WebAuthenticationDetailsSource().buildDetails(request)

                // 3-6) SecurityContext 에 Authentication 저장
                //      -> 이후 컨트롤러에서는 @AuthenticationPrincipal 로 principal 사용 가능
                SecurityContextHolder.getContext().authentication = auth
            }
        }

        // 4) 나머지 필터 체인 계속 진행
        filterChain.doFilter(request, response)
    }

    /**
     * HTTP 요청 헤더에서 JWT 토큰을 꺼내는 역할
     *
     * 우선 표준 Authorization 헤더(Bearer 토큰)를 보고,
     * 없으면 기존 호환을 위해 "jwt-token" 헤더도 허용.
     */
    private fun resolveToken(request: HttpServletRequest): String? {
        // 1) 표준: Authorization 헤더 (예: "Authorization: Bearer eyJ...")
        val bearer = request.getHeader("Authorization")
        if (!bearer.isNullOrBlank() && bearer.startsWith("Bearer ", ignoreCase = true)) {
            // "Bearer " 이후의 실제 토큰 문자열만 잘라서 반환
            return bearer.substring(7)
        }

        // 2) 이전 코드와의 호환: "jwt-token" 헤더가 있다면 그것도 토큰으로 간주
        val legacy = request.getHeader("jwt-token")
        if (!legacy.isNullOrBlank()) {
            return legacy
        }

        // 3) 둘 다 없으면 null
        return null
    }
}
