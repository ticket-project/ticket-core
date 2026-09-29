/**
 * entity가 상속하는 JPA 기반 타입만 둔다. {@code shared.api}(jakarta.persistence 참조 금지)나 {@code persistence}(domain의 참조 금지)라는 이름에는
 * 넣을 수 없어 별도 named interface다 — {@code ArchitectureRulesTest}의 두 규칙이 그 이유다.
 */
@NullMarked
@org.springframework.modulith.NamedInterface("jpa")
package com.ticket.shared.jpa;

import org.jspecify.annotations.NullMarked;
