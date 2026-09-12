/**
 * 애플리케이션 전역 HTTP 인증·인가 정책을 소유하는 기술 모듈이다.
 *
 * <p>Authorization header를 member의 공개 access token 검증 계약으로 해석해
 * {@code SecurityContext}를 구성하고, API URL별 접근 정책과 MVC 인증 주체 전달을 제공한다.
 * JWT 발급·검증 구현, 로그인·로그아웃·refresh, OAuth2 provider 구현은 member가 소유한다.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Web Security",
        allowedDependencies = {
                "member", "member :: exception",
                "shared", "shared :: web", "shared :: exception"
        }
)
package com.ticket.security;
