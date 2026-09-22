package com.ticket.security.oauth;

import java.util.Map;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GetSocialLoginUrlsUseCase {
    private static final String AUTHORIZATION_BASE_URI = "/api/v1/auth/oauth2/authorize";
    private static final String GOOGLE_REGISTRATION_ID = "google";
    private static final String KAKAO_REGISTRATION_ID = "kakao";

    public record Input(String baseUrl) {}

    public record Output(Map<String, String> urls) {}

    public Output execute(final Input input) {
        final String normalizedBaseUrl = normalizeBaseUrl(input.baseUrl());
        return new Output(Map.of(
                GOOGLE_REGISTRATION_ID,
                buildSocialLoginUrl(normalizedBaseUrl, GOOGLE_REGISTRATION_ID),
                KAKAO_REGISTRATION_ID,
                buildSocialLoginUrl(normalizedBaseUrl, KAKAO_REGISTRATION_ID)));
    }

    private String buildSocialLoginUrl(final String baseUrl, final String registrationId) {
        return baseUrl + AUTHORIZATION_BASE_URI + "/" + registrationId;
    }

    /** baseUrl은 설정 값이 아니라 호출자가 현재 요청에서 만들어 넘기는 값이다({@code AuthController}). */
    private String normalizeBaseUrl(final String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("baseUrl must not be blank");
        }
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }
}
