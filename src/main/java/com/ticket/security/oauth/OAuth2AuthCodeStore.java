package com.ticket.security.oauth;

import java.util.Optional;

public interface OAuth2AuthCodeStore {
    String createCode(Long memberId);

    Optional<Long> consumeCode(String code);
}
