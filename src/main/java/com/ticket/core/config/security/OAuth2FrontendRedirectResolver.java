package com.ticket.core.config.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;

@Component
public class OAuth2FrontendRedirectResolver {

    static final String SESSION_ATTRIBUTE = "oauth2.frontend-base-url";
    private static final String LOCALHOST = "localhost";
    private static final String LOOPBACK = "127.0.0.1";

    private final String localFrontendBaseUrl;
    private final String prodFrontendBaseUrl;
    private final String successRedirectPath;
    private final String failureRedirectPath;
    private final String defaultSuccessRedirectUri;
    private final String defaultFailureRedirectUri;

    public OAuth2FrontendRedirectResolver(
            @Value("${app.auth.frontend.local-base-url:http://localhost:3000}") final String localFrontendBaseUrl,
            @Value("${app.auth.frontend.prod-base-url:https://oneticket.site}") final String prodFrontendBaseUrl,
            @Value("${app.auth.oauth2-success-redirect-path:/auth/callback}") final String successRedirectPath,
            @Value("${app.auth.oauth2-failure-redirect-path:/auth/callback}") final String failureRedirectPath,
            @Value("${app.auth.oauth2-success-redirect-uri}") final String defaultSuccessRedirectUri,
            @Value("${app.auth.oauth2-failure-redirect-uri}") final String defaultFailureRedirectUri
    ) {
        this.localFrontendBaseUrl = normalizeBaseUrl(localFrontendBaseUrl);
        this.prodFrontendBaseUrl = normalizeBaseUrl(prodFrontendBaseUrl);
        this.successRedirectPath = normalizePath(successRedirectPath);
        this.failureRedirectPath = normalizePath(failureRedirectPath);
        this.defaultSuccessRedirectUri = defaultSuccessRedirectUri;
        this.defaultFailureRedirectUri = defaultFailureRedirectUri;
    }

    public void storeFrontendBaseUrl(final HttpServletRequest request) {
        final String frontendBaseUrl = resolveFrontendBaseUrl(request);
        if (frontendBaseUrl == null) {
            return;
        }
        request.getSession(true).setAttribute(SESSION_ATTRIBUTE, frontendBaseUrl);
    }

    public String resolveSuccessRedirectUri(final HttpServletRequest request) {
        return resolveRedirectUri(request, successRedirectPath, defaultSuccessRedirectUri);
    }

    public String resolveFailureRedirectUri(final HttpServletRequest request) {
        return resolveRedirectUri(request, failureRedirectPath, defaultFailureRedirectUri);
    }

    public void clear(final HttpServletRequest request) {
        final HttpSession session = request.getSession(false);
        if (session == null) {
            return;
        }
        session.removeAttribute(SESSION_ATTRIBUTE);
    }

    private String resolveRedirectUri(
            final HttpServletRequest request,
            final String path,
            final String fallbackRedirectUri
    ) {
        final HttpSession session = request.getSession(false);
        if (session == null) {
            return fallbackRedirectUri;
        }
        final Object stored = session.getAttribute(SESSION_ATTRIBUTE);
        if (!(stored instanceof String frontendBaseUrl) || frontendBaseUrl.isBlank()) {
            return fallbackRedirectUri;
        }
        return normalizeBaseUrl(frontendBaseUrl) + path;
    }

    private String resolveFrontendBaseUrl(final HttpServletRequest request) {
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

    private boolean isLocal(final String uri) {
        final String host = host(uri);
        return LOCALHOST.equals(host) || LOOPBACK.equals(host);
    }

    private boolean hasHost(final String uri) {
        return host(uri) != null;
    }

    private String host(final String uri) {
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
