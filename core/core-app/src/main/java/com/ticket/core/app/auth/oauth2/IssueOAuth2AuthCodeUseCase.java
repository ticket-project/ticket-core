package com.ticket.core.app.auth.oauth2;

import com.ticket.core.domain.auth.oauth2.OAuth2AuthCodeStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 소셜 로그인 성공 직후 프론트엔드에 넘길 1회용 auth code를 발급한다.
 * 저장소 포트를 실행 모듈이 직접 부르지 않도록 이 use case가 감싼다.
 */
@Service
@RequiredArgsConstructor
public class IssueOAuth2AuthCodeUseCase {

    private final OAuth2AuthCodeStore oAuth2AuthCodeStore;

    public String execute(final Long memberId) {
        return oAuth2AuthCodeStore.createCode(memberId);
    }
}
