package com.ticket.security.oauth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;

/**
 * 소셜 로그인(OAuth2) 경로의 filter chain.
 *
 * <p>서버 세션을 쓰지 않는다(스케일 아웃 결정 D2-3). 로그인 진행 정보는 암호화 쿠키 ({@link EncryptedCookieOAuth2AuthorizationRequestRepository})에,
 * 인증 결과는 이번 요청에만 두고, 제공자 토큰은 저장하지 않는다. 로그인 시작과 콜백이 다른 인스턴스로 가도 동작하고, 인증 전 요청이 서버에 아무것도 쌓지 않는다.
 */
@Configuration
public class OAuth2SecurityConfig {
    @Bean
    @Order(1)
    public SecurityFilterChain oauth2FilterChain(
            final HttpSecurity http,
            final CustomOAuth2UserService customOAuth2UserService,
            final EncryptedCookieOAuth2AuthorizationRequestRepository authorizationRequestRepository,
            final OAuth2AuthenticationSuccessHandler oauth2AuthenticationSuccessHandler,
            final OAuth2AuthenticationFailureHandler oauth2AuthenticationFailureHandler)
            throws Exception {
        http.securityMatcher(
                        OAuth2EndpointConstants.AUTHORIZATION_BASE_URI + "/**",
                        OAuth2EndpointConstants.CALLBACK_BASE_URI_PATTERN)
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .securityContext(
                        context -> context.securityContextRepository(new RequestAttributeSecurityContextRepository()))
                .requestCache(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .oauth2Login(oauth2 -> oauth2.authorizationEndpoint(authorization -> authorization
                                .baseUri(OAuth2EndpointConstants.AUTHORIZATION_BASE_URI)
                                .authorizationRequestRepository(authorizationRequestRepository))
                        .redirectionEndpoint(
                                redirection -> redirection.baseUri(OAuth2EndpointConstants.CALLBACK_BASE_URI_PATTERN))
                        .userInfoEndpoint(userInfo -> userInfo.userService(customOAuth2UserService))
                        .authorizedClientRepository(new DiscardingOAuth2AuthorizedClientRepository())
                        .successHandler(oauth2AuthenticationSuccessHandler)
                        .failureHandler(oauth2AuthenticationFailureHandler));

        return http.build();
    }
}
