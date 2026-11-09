package com.ticket.security.oauth;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.stereotype.Component;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * OAuth2 로그인 진행 정보(authorization request: state, redirect_uri, nonce 등)를 서버 세션 대신 AES-GCM으로 암호화한 쿠키에 담는다(스케일 아웃 결정
 * D2-3).
 *
 * <p>로그인 시작과 콜백이 다른 인스턴스로 가도 같은 키로 풀 수 있고, 인증 전 요청이 서버 메모리나 Redis에 아무것도 쌓지 않는다.
 *
 * <ul>
 *   <li>쿠키 이름에 state의 해시를 넣어 탭마다 다른 쿠키를 쓴다. 두 탭에서 동시에 로그인을 시작해도 서로 덮어쓰지 않는다.
 *   <li>{@code __Host-} 접두어(Secure, Path=/, Domain 없음)로 하위 도메인이 같은 이름의 쿠키를 심지 못하게 한다.
 *   <li>암호화 때 쿠키 이름을 AAD로 묶어, 다른 state의 쿠키 값을 옮겨 붙이면 복호화가 실패한다.
 *   <li>JSON으로 직렬화한다(Java 직렬화를 쓰지 않는다). 만료 시각을 값 안에도 넣어 오래된 값을 거절한다.
 *   <li>콜백 경로의 제공자와 저장한 제공자가 다르면 거절한다.
 *   <li>로그인을 시작한 프런트엔드 주소도 같은 쿠키에 담고, 콜백에서 꺼내 {@link OAuth2FrontendRedirectResolver}에 넘긴다.
 * </ul>
 *
 * <p>키는 {@code app.auth.oauth2-request-cookie-key}(Base64, 32바이트)이며 기본값이 없다. 없거나 길이가 다르면 기동이 실패한다. 키를 바꾸면 그 순간 로그인을 진행
 * 중이던 사람만 다시 시작한다.
 */
@Component
public class EncryptedCookieOAuth2AuthorizationRequestRepository
        implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {
    static final String COOKIE_NAME_PREFIX = "__Host-oauth2_req_";
    static final Duration MAX_AGE = Duration.ofMinutes(10);

    private static final int KEY_BYTES = 32;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final String CIPHER = "AES/GCM/NoPadding";
    private static final String CALLBACK_PATH_PREFIX = OAuth2EndpointConstants.CALLBACK_BASE_URI_PATTERN.substring(
            0, OAuth2EndpointConstants.CALLBACK_BASE_URI_PATTERN.length() - 1);

    private final SecretKey key;
    private final JsonMapper jsonMapper;
    private final OAuth2FrontendRedirectResolver frontendRedirectResolver;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public EncryptedCookieOAuth2AuthorizationRequestRepository(
            @Value("${app.auth.oauth2-request-cookie-key}") final String base64Key,
            final JsonMapper jsonMapper,
            final OAuth2FrontendRedirectResolver frontendRedirectResolver,
            final Clock clock) {
        this.key = new SecretKeySpec(decodeKey(base64Key), "AES");
        this.jsonMapper = jsonMapper;
        this.frontendRedirectResolver = frontendRedirectResolver;
        this.clock = clock;
    }

    @Override
    public @Nullable OAuth2AuthorizationRequest loadAuthorizationRequest(final HttpServletRequest request) {
        final String state = request.getParameter(OAuth2ParameterNames.STATE);
        if (state == null) {
            return null;
        }
        final StoredAuthorizationRequest stored = read(request, state);
        return stored == null ? null : stored.toAuthorizationRequest();
    }

    @Override
    public void saveAuthorizationRequest(
            final OAuth2AuthorizationRequest authorizationRequest,
            final HttpServletRequest request,
            final HttpServletResponse response) {
        final String state = authorizationRequest.getState();
        if (state == null) {
            throw new IllegalStateException("state가 없는 OAuth2 authorization request는 저장할 수 없습니다.");
        }
        final StoredAuthorizationRequest stored = StoredAuthorizationRequest.of(
                authorizationRequest,
                frontendRedirectResolver.resolveFrontendBaseUrl(request),
                clock.instant().plus(MAX_AGE).getEpochSecond());
        final String name = cookieName(state);
        writeCookie(response, name, encrypt(jsonMapper.writeValueAsBytes(stored), name), MAX_AGE);
    }

    @Override
    public @Nullable OAuth2AuthorizationRequest removeAuthorizationRequest(
            final HttpServletRequest request, final HttpServletResponse response) {
        final String state = request.getParameter(OAuth2ParameterNames.STATE);
        if (state == null) {
            return null;
        }
        final StoredAuthorizationRequest stored = read(request, state);
        writeCookie(response, cookieName(state), "", Duration.ZERO);
        if (stored == null) {
            return null;
        }
        frontendRedirectResolver.rememberFrontendBaseUrl(request, stored.frontendBaseUrl());
        return stored.toAuthorizationRequest();
    }

    private @Nullable StoredAuthorizationRequest read(final HttpServletRequest request, final String state) {
        final String name = cookieName(state);
        final String value = findCookie(request, name);
        if (value == null) {
            return null;
        }
        final StoredAuthorizationRequest stored;
        try {
            stored = jsonMapper.readValue(decrypt(value, name), StoredAuthorizationRequest.class);
        } catch (final GeneralSecurityException | IllegalArgumentException | JacksonException e) {
            // 변조, 다른 키, 다른 state의 값을 옮겨 붙인 경우다. 로그인 실패(authorization_request_not_found)로 끝난다.
            return null;
        }
        if (!state.equals(stored.state())
                || stored.expiresAtEpochSecond() <= clock.instant().getEpochSecond()) {
            return null;
        }
        if (!matchesCallbackProvider(request, stored)) {
            return null;
        }
        return stored;
    }

    private boolean matchesCallbackProvider(final HttpServletRequest request, final StoredAuthorizationRequest stored) {
        final String uri = request.getRequestURI();
        if (uri == null || !uri.startsWith(CALLBACK_PATH_PREFIX)) {
            return true;
        }
        final String callbackRegistrationId = uri.substring(CALLBACK_PATH_PREFIX.length());
        return callbackRegistrationId.equals(stored.attributes().get(OAuth2ParameterNames.REGISTRATION_ID));
    }

    private byte[] encrypt(final byte[] plain, final String cookieName) {
        try {
            final byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            final Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(cookieName.getBytes(StandardCharsets.US_ASCII));
            final byte[] encrypted = cipher.doFinal(plain);
            final byte[] joined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, joined, 0, iv.length);
            System.arraycopy(encrypted, 0, joined, iv.length, encrypted.length);
            return joined;
        } catch (final GeneralSecurityException e) {
            throw new IllegalStateException("OAuth2 authorization request 쿠키를 암호화하지 못했습니다.", e);
        }
    }

    private byte[] decrypt(final String value, final String cookieName) throws GeneralSecurityException {
        final byte[] joined = Base64.getUrlDecoder().decode(value);
        if (joined.length <= IV_BYTES) {
            throw new IllegalArgumentException("암호문이 너무 짧습니다.");
        }
        final Cipher cipher = Cipher.getInstance(CIPHER);
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, joined, 0, IV_BYTES));
        cipher.updateAAD(cookieName.getBytes(StandardCharsets.US_ASCII));
        return cipher.doFinal(joined, IV_BYTES, joined.length - IV_BYTES);
    }

    private void writeCookie(
            final HttpServletResponse response, final String name, final byte[] value, final Duration maxAge) {
        writeCookie(response, name, Base64.getUrlEncoder().withoutPadding().encodeToString(value), maxAge);
    }

    private void writeCookie(
            final HttpServletResponse response, final String name, final String value, final Duration maxAge) {
        final ResponseCookie cookie = ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(true)
                .path("/")
                .sameSite("Lax")
                .maxAge(maxAge)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private static @Nullable String findCookie(final HttpServletRequest request, final String name) {
        final Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (final Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    /** state에는 쿠키 이름에 쓸 수 없는 '='가 들어갈 수 있어 해시로 바꾼다. */
    static String cookieName(final String state) {
        try {
            final byte[] digest = MessageDigest.getInstance("SHA-256").digest(state.getBytes(StandardCharsets.UTF_8));
            return COOKIE_NAME_PREFIX + HexFormat.of().formatHex(digest, 0, 16);
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] decodeKey(final String base64Key) {
        final byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(base64Key.trim());
        } catch (final IllegalArgumentException e) {
            throw new IllegalStateException("app.auth.oauth2-request-cookie-key는 Base64여야 합니다.", e);
        }
        if (decoded.length != KEY_BYTES) {
            throw new IllegalStateException(
                    "app.auth.oauth2-request-cookie-key는 Base64로 인코딩한 32바이트여야 합니다. 현재 " + decoded.length + "바이트");
        }
        return decoded;
    }

    /** 쿠키에 담는 값. 인가 코드 흐름만 쓰므로 grant type은 저장하지 않는다. */
    record StoredAuthorizationRequest(
            String authorizationUri,
            String clientId,
            @Nullable String redirectUri,
            Set<String> scopes,
            String state,
            Map<String, Object> additionalParameters,
            String authorizationRequestUri,
            Map<String, Object> attributes,
            @Nullable String frontendBaseUrl,
            long expiresAtEpochSecond) {
        static StoredAuthorizationRequest of(
                final OAuth2AuthorizationRequest request,
                final @Nullable String frontendBaseUrl,
                final long expiresAtEpochSecond) {
            final String state = request.getState();
            if (state == null) {
                throw new IllegalStateException("state가 없는 OAuth2 authorization request입니다.");
            }
            return new StoredAuthorizationRequest(
                    request.getAuthorizationUri(),
                    request.getClientId(),
                    request.getRedirectUri(),
                    request.getScopes(),
                    state,
                    request.getAdditionalParameters(),
                    request.getAuthorizationRequestUri(),
                    request.getAttributes(),
                    frontendBaseUrl,
                    expiresAtEpochSecond);
        }

        OAuth2AuthorizationRequest toAuthorizationRequest() {
            return OAuth2AuthorizationRequest.authorizationCode()
                    .authorizationUri(authorizationUri)
                    .clientId(clientId)
                    .redirectUri(redirectUri)
                    .scopes(scopes)
                    .state(state)
                    .additionalParameters(additionalParameters)
                    .authorizationRequestUri(authorizationRequestUri)
                    .attributes(attributes)
                    .build();
        }
    }
}
