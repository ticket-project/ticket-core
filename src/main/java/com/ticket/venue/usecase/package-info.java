/**
 * venue 공개 계약({@code venue.api})의 구현이다.
 *
 * <p>domain Repository가 돌려준 엔티티를 공개 계약 타입으로 한 번만 변환한다 — venue JPA entity는 module 밖으로 나가지 않는다. Aggregate마다 구현을
 * 나눈다({@code VenueLookupService}, {@code SeatLookupService}).
 */
@NullMarked
package com.ticket.venue.usecase;

import org.jspecify.annotations.NullMarked;
