package com.ticket.security.oauth;

import java.net.URI;

import jakarta.servlet.http.HttpServletRequest;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 소셜 로그인을 시작한 프런트엔드(로컬·운영)를 Origin·Referer로 정하고, 콜백에서 그 프런트엔드로 돌려보낼 주소를 만든다.
 *
 * <p>정한 주소는 서버 세션이 아니라 {@link EncryptedCookieOAuth2AuthorizationRequestRepository}가 authorization request와 같은 암호화 쿠키에
 * 담는다. 콜백에서 쿠키를 꺼낼 때 요청 속성으로 넘겨받는다(스케일 아웃 결정 D2-3).
 */
@Component
public class OAuth2FrontendRedirectResolver {
    static final String REQUEST_ATTRIBUTE = "oauth2.frontend-base-url";
    private static final String LOCALHOST = "localhost";
    private static final String LOOPBACK = "127.0.0.1";
    private final String localFrontendBaseUrl;
    private final String prodFrontendBaseUrl;
    private final String successRedirectPath;
    private final String failureRedirectPath;
    private final String defaultSuccessRedirectUri;
    private final String defaultFailureRedirectUri;

    public OAuth2FrontendRedirectResolver(
            @Value("${app.auth.frontend.local-base-url}") final String localFrontendBaseUrl,
            @Value("${app.auth.frontend.prod-base-url}") final String prodFrontendBaseUrl,
            @Value("${app.auth.oauth2-success-redirect-path}") final String successRedirectPath,
            @Value("${app.auth.oauth2-failure-redirect-path}") final String failureRedirectPath,
            @Value("${app.auth.oauth2-success-redirect-uri}") final String defaultSuccessRedirectUri,
            @Value("${app.auth.oauth2-failure-redirect-uri}") final String defaultFailureRedirectUri) {
        this.localFrontendBaseUrl = normalizeBaseUrl(localFrontendBaseUrl);
        this.prodFrontendBaseUrl = normalizeBaseUrl(prodFrontendBaseUrl);
        this.successRedirectPath = normalizePath(successRedirectPath);
        this.failureRedirectPath = normalizePath(failureRedirectPath);
        this.defaultSuccessRedirectUri = defaultSuccessRedirectUri;
        this.defaultFailureRedirectUri = defaultFailureRedirectUri;
    }

    /** 콜백에서 쿠키로 꺼낸 프런트엔드 주소를 이번 요청에만 둔다. 성공·실패 handler가 읽는다. */
    void rememberFrontendBaseUrl(final HttpServletRequest request, final @Nullable String frontendBaseUrl) {
        if (frontendBaseUrl != null) {
            request.setAttribute(REQUEST_ATTRIBUTE, frontendBaseUrl);
        }
    }

    public String resolveSuccessRedirectUri(final HttpServletRequest request) {
        return resolveRedirectUri(request, successRedirectPath, defaultSuccessRedirectUri);
    }

    public String resolveFailureRedirectUri(final HttpServletRequest request) {
        return resolveRedirectUri(request, failureRedirectPath, defaultFailureRedirectUri);
    }

    private String resolveRedirectUri(
            final HttpServletRequest request, final String path, final String fallbackRedirectUri) {
        final Object stored = request.getAttribute(REQUEST_ATTRIBUTE);
        if (!(stored instanceof String frontendBaseUrl) || frontendBaseUrl.isBlank()) {
            return fallbackRedirectUri;
        }
        // resolveFrontendBaseUrl이 고른 설정값만 쿠키에 담긴다.
        return frontendBaseUrl + path;
    }

    @Nullable
    String resolveFrontendBaseUrl(final HttpServletRequest request) {
        final String origin = request.getHeader("Origin");
        final String referer = request.getHeader("Referer");
        if (isLocal(origin) || isLocal(referer)) {
            return localFrontendBaseUrl;
        }
        if (hasHost(origin) || hasHost(referer)) {
            return prodFrontendBaseUrl;
        }
        return null;
    }

    private boolean isLocal(final @Nullable String uri) {
        final String host = host(uri);
        return LOCALHOST.equals(host) || LOOPBACK.equals(host);
    }

    private boolean hasHost(final @Nullable String uri) {
        return host(uri) != null;
    }

    private @Nullable String host(final @Nullable String uri) {
        if (uri == null || uri.isBlank()) {
            return null;
        }
        try {
            return URI.create(uri).getHost();
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private String normalizeBaseUrl(final String baseUrl) {
        if (baseUrl.endsWith("/")) {
            return baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl;
    }

    private String normalizePath(final String path) {
        if (path.startsWith("/")) {
            return path;
        }
        return "/" + path;
    }
}
