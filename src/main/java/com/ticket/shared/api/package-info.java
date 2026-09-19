/**
 * 모든 module이 함께 쓰는 기술 중립 공유 계약이다.
 *
 * <p>{@link com.ticket.shared.api.CursorPage}(커서 페이징 조회 결과), {@link
 * com.ticket.shared.api.CorsProperties}(security의 {@code ApiSecurityConfig}와 booking의 {@code
 * WebSocketConfig}가 함께 읽는 값), {@link com.ticket.shared.api.AuditorPrincipal}(인증 주체의 기술 중립 감사 식별자
 * 계약)이 여기 있다.
 *
 * <p>여기 두는 class는 특정 Entity나 기술 adapter가 아니고 업무 vocabulary를 전혀 담지 않는, 둘 이상의 독립 module이 의미 그대로 공유하는
 * 타입이어야 한다. shared의 나머지 공개 계약은 성격이 달라 각자의 named interface에 있다 — HTTP 응답 형식은 {@code shared.web}, 공통
 * 오류 계약은 {@code shared.exception}, entity가 상속하는 JPA 기반 타입은 {@code shared.jpa}다. 넷을 하나로 합치지 않는다.
 *
 * <p>{@code AuditedEntity}가 여기 없는 이유는 {@code ArchitectureRulesTest.api는_구현_기술을_모른다}다 — 이 package는
 * {@code jakarta.persistence}를 참조할 수 없다.
 *
 * <p><b>{@code AuditorPrincipal}에 {@code Api} 접미사를 붙이지 않는다.</b> 다른 module이 <b>호출하는</b> 계약이 아니라
 * <b>구현하는</b> 계약(SPI)이라, 접미사가 방향을 거꾸로 읽히게 만든다.
 */
@NullMarked
@org.springframework.modulith.NamedInterface("api")
package com.ticket.shared.api;

import org.jspecify.annotations.NullMarked;
