package com.ticket.security.oauth;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

/** 소셜 로그인 성공 직후 프론트엔드에 넘길 1회용 auth code를 발급한다. 저장소 포트를 실행 모듈이 직접 부르지 않도록 이 use case가 감싼다. */
@Service
@RequiredArgsConstructor
public class IssueOAuth2AuthCodeUseCase {
    private final OAuth2AuthCodeStore oauth2AuthCodeStore;

    public String execute(final Long memberId) {
        return oauth2AuthCodeStore.createCode(memberId);
    }
}
