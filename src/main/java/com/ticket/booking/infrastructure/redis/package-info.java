/**
 * booking의 Redis 구현이다.
 *
 * <p>hold와 좌석 선점의 저장({@code Redisson*Store}), 그 key 형식({@code *RedisKey}), 분산락 구현, 그리고 키 만료 이벤트 수신과
 * 그 후속 처리({@code *ExpirationHandler})가 함께 있다. key 형식과 TTL은 여기서만 정한다 — application의 락 계약은 Redis를
 * 모른다.
 */
@NullMarked
package com.ticket.booking.infrastructure.redis;

import org.jspecify.annotations.NullMarked;
