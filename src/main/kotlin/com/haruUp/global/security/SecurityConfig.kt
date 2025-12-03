package com.haruUp.global.security

import com.haruUp.member.application.service.MemberService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

/**
 * Spring Security 전역 설정 클래스.
 *
 * 역할:
 *  - 어떤 요청을 인증 없이 허용할지 / 인증 필요하게 막을지 설정
 *  - 세션을 상태 없음(STATELESS)으로 만들고 JWT 방식으로 인증 수행
 *  - 비밀번호 인코더, AuthenticationManager, JWT 필터 Bean 등록
 */
@Configuration
@EnableWebSecurity               // Spring Security 웹 보안 활성화
@EnableMethodSecurity            // @PreAuthorize, @PostAuthorize 등 메서드 단위 보안 활성화
class SecurityConfig(
    private val jwtTokenProvider: JwtTokenProvider,
    private val memberService: MemberService,
) {

    /**
     * 비밀번호 인코더 Bean.
     *
     * - 회원가입 시 평문 비밀번호 -> 해시값으로 인코딩
     * - 로그인 시 입력한 비밀번호와 저장된 해시값 비교할 때 사용
     */
    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    /**
     * AuthenticationManager Bean.
     *
     * - UsernamePasswordAuthenticationToken 같은 표준 인증 토큰을 처리할 때 사용.
     * - JWT 기반 인증에서는 직접 쓸 일이 많지는 않지만,
     *   일부 상황 (폼 로그인, 커스텀 인증 로직)에서 필요할 수 있어서 등록.
     */
    @Bean
    fun authenticationFilter(config: AuthenticationConfiguration): AuthenticationManager =
        config.authenticationManager

    /**
     * JwtAuthenticationFilter Bean.
     *
     * - 매 요청마다 실행되며, Authorization 헤더의 JWT를 검증하고
     *   유효하면 SecurityContext에 인증 정보를 세팅하는 역할.
     */
    @Bean
    fun jwtAuthenticationFilter(): JwtAuthenticationFilter =
        JwtAuthenticationFilter(jwtTokenProvider, memberService)

    /**
     * HTTP 보안 설정의 핵심.
     *
     * - CSRF 비활성화 (JWT 기반이라 서버 세션을 사용하지 않기 때문)
     * - 세션 정책을 STATELESS 로 설정 (매 요청마다 토큰으로 인증)
     * - 특정 URL은 permitAll()로 열어놓고, 나머지 요청은 authenticated()로 잠금
     * - UsernamePasswordAuthenticationFilter 앞에 JwtAuthenticationFilter를 추가
     */
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            // 1) CSRF 설정
            //    - 주로 브라우저 기반 폼 로그인에서 사용되는 CSRF 보호 기능
            //    - REST API + JWT 조합에서는 보통 사용하지 않으므로 disable
            .csrf { csrf ->
                csrf.disable()
            }

            // 2) 세션 정책 설정
            //    - STATELESS: 서버가 HTTP 세션을 생성/유지하지 않음
            //    - 매 요청마다 클라이언트가 Access Token(JWT)을 보내서 인증
            .sessionManagement { session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            }

            // 3) URL 별 인가(Authorization) 규칙 설정
            .authorizeHttpRequests { auth ->
                auth
                    // 3-1) 로그인/회원가입/토큰 재발급 등 인증 없이 접근 가능한 경로
                    .requestMatchers(
                        "/api/member/auth/**",  // Auth 관련 엔드포인트는 모두 허용
                        "/v3/api-docs/**",      // Swagger/OpenAPI 문서
                        "/swagger-ui/**",
                        "/swagger-ui.html"
                    ).permitAll()
                    // 3-2) 위에서 명시한 경로를 제외한 나머지 모든 요청은 인증 필요
                    .anyRequest().authenticated()
            }

            // 4) 필터 체인에 커스텀 JWT 필터 추가
            //    - UsernamePasswordAuthenticationFilter 실행 "이전"에 JWT 필터가 동작하도록 설정
            //    - 즉, 폼 로그인 인증 전에 JWT 인증을 먼저 시도
            .addFilterBefore(
                jwtAuthenticationFilter(),
                UsernamePasswordAuthenticationFilter::class.java
            )

        // 5) 최종적으로 구성된 SecurityFilterChain 을 반환
        return http.build()
    }
}
