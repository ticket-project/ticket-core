package com.ticket.shared;

/**
 * 인증 주체가 JPA 감사 로그에 남길 식별자를 제공하는 기술 중립 계약이다.
 *
 * <p>{@code java.security.Principal}은 Spring MVC가 controller argument로 먼저 해석하므로,
 * 애플리케이션의 인증 주체 값 타입에는 이 별도 계약을 사용한다.
 */
public interface AuditorPrincipal {

    String auditorId();
}
