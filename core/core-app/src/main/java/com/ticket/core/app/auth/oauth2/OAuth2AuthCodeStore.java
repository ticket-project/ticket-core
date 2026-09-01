package com.ticket.core.app.auth.oauth2;

import java.util.Optional;

public interface OAuth2AuthCodeStore {

    String createCode(Long memberId);

    Optional<Long> consumeCode(String code);
}
