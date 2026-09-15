package com.ticket.security.token;

import java.util.UUID;

/**
 * refresh token 값과 OAuth2 one-time auth code에 쓸 새 식별자를 만든다.
 *
 * <p>별도 계약인 이유는 <b>테스트가 발급되는 값을 고정할 수 있어야</b> 하기 때문이다. 저장소가 실제로 그 값을 key로 썼는지 확인하려면 무엇이 발급됐는지 알아야
 * 하는데, {@code UUID.randomUUID()}를 직접 부르면 그럴 수 없다.
 */
@FunctionalInterface
public interface UuidSupplier {
    UUID newUuid();
}
