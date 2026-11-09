package com.ticket.security.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Map;
import java.util.Set;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;

import tools.jackson.databind.json.JsonMapper;

@SuppressWarnings("NonAsciiCharacters")
class EncryptedCookieOAuth2AuthorizationRequestRepositoryTest {
    private static final String KEY = Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes());
    private static final String OTHER_KEY =
            Base64.getEncoder().encodeToString("fedcba9876543210fedcba9876543210".getBytes());
    private static final Instant NOW = Instant.parse("2026-11-09T00:00:00Z");
    private static final String GOOGLE_CALLBACK = "/api/v1/auth/oauth2/callback/google";

    private final OAuth2FrontendRedirectResolver resolver = new OAuth2FrontendRedirectResolver(
            "http://localhost:3000",
            "https://oneticket.site",
            "/auth/callback",
            "/auth/callback",
            "https://oneticket.site/auth/callback",
            "https://oneticket.site/auth/callback");

    @Test
    void 저장한_요청을_같은_키를_가진_다른_인스턴스가_콜백에서_꺼낸다() {
        OAuth2AuthorizationRequest original = authorizationRequest("state-a", "google");
        Cookie cookie = save(repository(KEY, NOW), original, "http://localhost:3000");

        MockHttpServletRequest callback = callback(GOOGLE_CALLBACK, "state-a", cookie);
        MockHttpServletResponse response = new MockHttpServletResponse();
        OAuth2AuthorizationRequest loaded =
                repository(KEY, NOW.plusSeconds(30)).removeAuthorizationRequest(callback, response);

        assertThat(loaded).isNotNull();
        assertThat(loaded.getState()).isEqualTo("state-a");
        assertThat(loaded.getClientId()).isEqualTo("client-id");
        assertThat(loaded.getRedirectUri()).isEqualTo("https://api.example/api/v1/auth/oauth2/callback/google");
        assertThat(loaded.getScopes()).containsExactlyInAnyOrder("openid", "email");
        assertThat(loaded.getAuthorizationRequestUri()).isEqualTo(original.getAuthorizationRequestUri());
        assertThat(loaded.getAdditionalParameters()).containsEntry("nonce", "hashed-nonce");
        assertThat(loaded.<String>getAttribute(OAuth2ParameterNames.REGISTRATION_ID))
                .isEqualTo("google");
        assertThat(loaded.<String>getAttribute("nonce")).isEqualTo("raw-nonce");
        assertThat(callback.getAttribute(OAuth2FrontendRedirectResolver.REQUEST_ATTRIBUTE))
                .isEqualTo("http://localhost:3000");
        assertThat(callback.getSession(false)).isNull();
    }

    @Test
    void 쿠키는_Host_접두어와_HttpOnly_Secure_SameSite_Lax_10분으로_내리고_꺼낼_때_지운다() {
        MockHttpServletResponse saveResponse = new MockHttpServletResponse();
        repository(KEY, NOW)
                .saveAuthorizationRequest(
                        authorizationRequest("state-a", "google"), new MockHttpServletRequest(), saveResponse);

        String setCookie = saveResponse.getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie)
                .startsWith(EncryptedCookieOAuth2AuthorizationRequestRepository.cookieName("state-a") + "=")
                .contains("Path=/", "Max-Age=600", "Secure", "HttpOnly", "SameSite=Lax")
                .doesNotContain("Domain=");

        MockHttpServletResponse removeResponse = new MockHttpServletResponse();
        repository(KEY, NOW)
                .removeAuthorizationRequest(
                        callback(GOOGLE_CALLBACK, "state-a", toCookie(saveResponse)), removeResponse);
        assertThat(removeResponse.getHeader(HttpHeaders.SET_COOKIE)).contains("Max-Age=0");
    }

    @Test
    void 두_탭이_동시에_로그인을_시작해도_각자의_state로_꺼낸다() {
        EncryptedCookieOAuth2AuthorizationRequestRepository repository = repository(KEY, NOW);
        Cookie tabA = save(repository, authorizationRequest("state-a", "google"), null);
        Cookie tabB = save(repository, authorizationRequest("state-b", "kakao"), null);

        assertThat(tabA.getName()).isNotEqualTo(tabB.getName());
        OAuth2AuthorizationRequest a = repository.removeAuthorizationRequest(
                callback(GOOGLE_CALLBACK, "state-a", tabA, tabB), new MockHttpServletResponse());
        OAuth2AuthorizationRequest b = repository.removeAuthorizationRequest(
                callback("/api/v1/auth/oauth2/callback/kakao", "state-b", tabA, tabB), new MockHttpServletResponse());

        assertThat(a).isNotNull();
        assertThat(a.getState()).isEqualTo("state-a");
        assertThat(b).isNotNull();
        assertThat(b.getState()).isEqualTo("state-b");
    }

    @Test
    void 변조한_값은_거절한다() {
        Cookie cookie = save(repository(KEY, NOW), authorizationRequest("state-a", "google"), null);
        char[] chars = cookie.getValue().toCharArray();
        chars[chars.length / 2] = chars[chars.length / 2] == 'A' ? 'B' : 'A';
        Cookie tampered = new Cookie(cookie.getName(), new String(chars));

        assertThat(repository(KEY, NOW).loadAuthorizationRequest(callback(GOOGLE_CALLBACK, "state-a", tampered)))
                .isNull();
    }

    @Test
    void 다른_키로_만든_값은_거절한다() {
        Cookie cookie = save(repository(OTHER_KEY, NOW), authorizationRequest("state-a", "google"), null);

        assertThat(repository(KEY, NOW).loadAuthorizationRequest(callback(GOOGLE_CALLBACK, "state-a", cookie)))
                .isNull();
    }

    @Test
    void 다른_state의_쿠키_값을_옮겨_붙이면_거절한다() {
        Cookie stateA = save(repository(KEY, NOW), authorizationRequest("state-a", "google"), null);
        Cookie movedToB = new Cookie(
                EncryptedCookieOAuth2AuthorizationRequestRepository.cookieName("state-b"), stateA.getValue());

        assertThat(repository(KEY, NOW).loadAuthorizationRequest(callback(GOOGLE_CALLBACK, "state-b", movedToB)))
                .isNull();
    }

    @Test
    void 만료_시각이_지난_값은_거절한다() {
        Cookie cookie = save(repository(KEY, NOW), authorizationRequest("state-a", "google"), null);
        Instant expired = NOW.plus(EncryptedCookieOAuth2AuthorizationRequestRepository.MAX_AGE);

        assertThat(repository(KEY, expired).loadAuthorizationRequest(callback(GOOGLE_CALLBACK, "state-a", cookie)))
                .isNull();
        assertThat(repository(KEY, expired.minus(Duration.ofSeconds(1)))
                        .loadAuthorizationRequest(callback(GOOGLE_CALLBACK, "state-a", cookie)))
                .isNotNull();
    }

    @Test
    void 콜백_제공자가_저장한_제공자와_다르면_거절한다() {
        Cookie cookie = save(repository(KEY, NOW), authorizationRequest("state-a", "google"), null);

        assertThat(repository(KEY, NOW)
                        .removeAuthorizationRequest(
                                callback("/api/v1/auth/oauth2/callback/kakao", "state-a", cookie),
                                new MockHttpServletResponse()))
                .isNull();
    }

    @Test
    void state가_없거나_쿠키가_없으면_null이다() {
        EncryptedCookieOAuth2AuthorizationRequestRepository repository = repository(KEY, NOW);

        assertThat(repository.loadAuthorizationRequest(new MockHttpServletRequest()))
                .isNull();
        assertThat(repository.removeAuthorizationRequest(
                        callback(GOOGLE_CALLBACK, "state-a"), new MockHttpServletResponse()))
                .isNull();
    }

    @Test
    void 키가_32바이트가_아니면_만들_수_없다() {
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);

        assertThatThrownBy(() -> repository(shortKey, NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32바이트");
        assertThatThrownBy(() -> repository("not base64!", NOW)).isInstanceOf(IllegalStateException.class);
    }

    private EncryptedCookieOAuth2AuthorizationRequestRepository repository(final String key, final Instant now) {
        return new EncryptedCookieOAuth2AuthorizationRequestRepository(
                key, JsonMapper.builder().build(), resolver, Clock.fixed(now, ZoneOffset.UTC));
    }

    private Cookie save(
            final EncryptedCookieOAuth2AuthorizationRequestRepository repository,
            final OAuth2AuthorizationRequest authorizationRequest,
            final String origin) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (origin != null) {
            request.addHeader("Origin", origin);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        repository.saveAuthorizationRequest(authorizationRequest, request, response);
        assertThat(request.getSession(false)).isNull();
        return toCookie(response);
    }

    private static Cookie toCookie(final MockHttpServletResponse response) {
        String header = response.getHeader(HttpHeaders.SET_COOKIE);
        String pair = header.substring(0, header.indexOf(';'));
        int separator = pair.indexOf('=');
        return new Cookie(pair.substring(0, separator), pair.substring(separator + 1));
    }

    private static MockHttpServletRequest callback(final String uri, final String state, final Cookie... cookies) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setParameter(OAuth2ParameterNames.STATE, state);
        request.setParameter(OAuth2ParameterNames.CODE, "provider-code");
        if (cookies.length > 0) {
            request.setCookies(cookies);
        }
        return request;
    }

    private static OAuth2AuthorizationRequest authorizationRequest(final String state, final String registrationId) {
        return OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri("https://accounts.example/authorize")
                .clientId("client-id")
                .redirectUri("https://api.example/api/v1/auth/oauth2/callback/" + registrationId)
                .scopes(Set.of("openid", "email"))
                .state(state)
                .additionalParameters(Map.of("nonce", "hashed-nonce"))
                .attributes(Map.of(OAuth2ParameterNames.REGISTRATION_ID, registrationId, "nonce", "raw-nonce"))
                .build();
    }
}
