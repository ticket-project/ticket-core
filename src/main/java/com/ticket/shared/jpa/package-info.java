/**
 * 모든 module의 entity가 상속하는 JPA 기반 타입이다.
 *
 * <p>{@link com.ticket.shared.jpa.AuditedEntity} 하나뿐이다. 감사 네 컬럼은 어느 BC에서도 같은 뜻이고 업무 vocabulary를 담지
 * 않아 module마다 복제할 이유가 없다 — 배경은 {@code docs/adr/0018-audit-base-entity-lives-in-shared.md}가 원본이다.
 *
 * <p><b>{@code shared.api}가 아니라 별도 named interface인 이유</b>: {@code shared.api}는 기술 중립 계약면이라 {@code
 * ArchitectureRulesTest.api는_구현_기술을_모른다}가 {@code jakarta.persistence}·{@code
 * org.springframework.data} 참조를 막는다. JPA를 드러내는 기반 타입은 그 자리에 못 들어간다.
 *
 * <p><b>{@code shared.persistence}가 아닌 이유</b>: {@code persistence}는 역할 이름이라 {@code
 * ArchitectureRulesTest.domain은_조립_저장_HTTP를_모른다}가 {@code ..domain..} → {@code ..persistence..}를
 * 막는다. entity는 domain에 있으므로 상속 자체가 위반이 된다.
 *
 * <p>여기에 저장 adapter나 Repository를 두지 않는다. entity가 상속하는 기반 타입만 둔다.
 */
@NullMarked
@org.springframework.modulith.NamedInterface("jpa")
package com.ticket.shared.jpa;

import org.jspecify.annotations.NullMarked;
