package com.ticket.member.oauth.infrastructure;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class OAuth2SecurityConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain oauth2FilterChain(
            final HttpSecurity http,
            final CustomOAuth2UserService customOAuth2UserService,
            final OAuth2FrontendRedirectResolver frontendRedirectResolver,
            final OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler,
            final OAuth2AuthenticationFailureHandler oAuth2AuthenticationFailureHandler
    ) throws Exception {
        http
                .securityMatcher(
                        OAuth2EndpointConstants.AUTHORIZATION_BASE_URI + "/**",
                        OAuth2EndpointConstants.CALLBACK_BASE_URI_PATTERN
                )
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .oauth2Login(oauth2 -> oauth2
                        .authorizationEndpoint(authorization ->
                                authorization.baseUri(OAuth2EndpointConstants.AUTHORIZATION_BASE_URI))
                        .redirectionEndpoint(redirection ->
                                redirection.baseUri(OAuth2EndpointConstants.CALLBACK_BASE_URI_PATTERN))
                        .userInfoEndpoint(userInfo -> userInfo.userService(customOAuth2UserService))
                        .successHandler(oAuth2AuthenticationSuccessHandler)
                        .failureHandler(oAuth2AuthenticationFailureHandler)
                );
        http.addFilterBefore(
                new OAuth2FrontendRedirectCaptureFilter(frontendRedirectResolver),
                OAuth2AuthorizationRequestRedirectFilter.class
        );

        return http.build();
    }
}
