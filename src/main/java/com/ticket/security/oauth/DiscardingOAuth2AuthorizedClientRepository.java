package com.ticket.security.oauth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.jspecify.annotations.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;

/**
 * 소셜 로그인 뒤 제공자 access token(authorized client)을 저장하지 않는다.
 *
 * <p>로그인이 끝나면 자체 JWT를 발급하고 제공자 토큰은 다시 쓰지 않는다(카카오 연결 끊기는 admin key를 쓴다). 기본 구현
 * ({@code InMemoryOAuth2AuthorizedClientService})은 로그인마다 토큰을 축출 없이 서버 메모리에 쌓고, 인스턴스마다 따로 쌓인다.
 */
class DiscardingOAuth2AuthorizedClientRepository implements OAuth2AuthorizedClientRepository {
    @Override
    public <T extends OAuth2AuthorizedClient> @Nullable T loadAuthorizedClient(
            final String clientRegistrationId, final Authentication principal, final HttpServletRequest request) {
        return null;
    }

    @Override
    public void saveAuthorizedClient(
            final OAuth2AuthorizedClient authorizedClient,
            final Authentication principal,
            final HttpServletRequest request,
            final HttpServletResponse response) {
        // 저장하지 않는다.
    }

    @Override
    public void removeAuthorizedClient(
            final String clientRegistrationId,
            final Authentication principal,
            final HttpServletRequest request,
            final HttpServletResponse response) {
        // 저장한 것이 없다.
    }
}
