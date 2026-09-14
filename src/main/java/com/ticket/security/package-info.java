/**
 * 인증·인가와 그 조립을 소유하는 기술 모듈이다. 회원 데이터는 갖지 않는다 — 계정은 member의 {@code MemberAccountApi} 공개 계약으로만 만진다.
 *
 * <p><b>이 module만 계층 대신 기능으로 나눈다.</b> 업무 모듈은 web/application/domain/infrastructure로 읽히지만, 여기 있는 것은
 * 업무가 아니라 인증 기술이라 "무엇에 관한 코드인가"가 더 나은 탐색 단위다. 각 폴더 안에는 계층 폴더를 다시 만들지 않는다.
 *
 * <ul>
 *   <li>{@code auth} — 가입·로그인·갱신·로그아웃·탈퇴 조립과 인증 Controller·요청·문서 타입
 *   <li>{@code jwt} — JWT 생성·검증·서명키·설정
 *   <li>{@code oauth} — OAuth filter chain·handler·provider 통신·응답 해석·인증 코드·외부 unlink
 *   <li>{@code token} — 토큰 발급·검증 계약과 결과, refresh token 저장, UUID 생성 기반
 *   <li>{@code http} — 일반 API 보안 설정·필터·SecurityContext·MVC 인증 주체·401/403·쿠키
 * </ul>
 *
 * <p>공개 계약: {@link com.ticket.security.api.AccessTokenAuthenticator} — HTTP filter chain을 타지 않는 경로
 * (booking의 WebSocket STOMP CONNECT)가 원본 access token 문자열을 검증할 때 쓴다. HTTP 경로의 만료/무효 구분은 {@code
 * security.token}의 읽기 계약이 보존하고, WebSocket은 그 구분이 필요 없어 하나의 인증 실패로 다룬다.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Security",
        allowedDependencies = {
            "member :: api",
            "member :: exception",
            "shared :: api",
            "shared :: web",
            "shared :: exception"
        })
package com.ticket.security;
