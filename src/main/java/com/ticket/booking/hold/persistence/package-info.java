/**
 * 선점 상태의 Redis 구현과 선점 이력의 JPA 구현이다.
 *
 * <p>선점 상태 자체는 Redis가 소유한다({@code RedissonHoldStore}) — key 형식과 TTL, meta 직렬화, 만료 알림 처리가 함께 있어야 한 곳에서 읽힌다. 이력은 감사 목적이라
 * JPA로 남긴다. 저장 계약({@code HoldStore}·{@code HoldHistoryRepository})은 {@code booking.hold.domain}에 있다.
 */
@NullMarked
package com.ticket.booking.hold.persistence;

import org.jspecify.annotations.NullMarked;
