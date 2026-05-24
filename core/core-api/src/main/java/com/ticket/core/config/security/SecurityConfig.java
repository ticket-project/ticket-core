package com.ticket.core.config.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableConfigurationProperties({JwtProperties.class, CorsProperties.class})
public class SecurityConfig {

    /**
     * OAuth2 전용 필터체인: 세션 허용 (OAuth2 state 검증에 필요)
     */
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
                        .userInfoEndpoint(userInfo ->
                                userInfo.userService(customOAuth2UserService))
                        .successHandler(oAuth2AuthenticationSuccessHandler)
                        .failureHandler(oAuth2AuthenticationFailureHandler)
                );
        http.addFilterBefore(
                new OAuth2FrontendRedirectCaptureFilter(frontendRedirectResolver),
                OAuth2AuthorizationRequestRedirectFilter.class
        );

        return http.build();
    }

    /**
     * API 전용 필터체인: 완전 Stateless (JWT 인증)
     */
    @Bean
    @Order(2)
    public SecurityFilterChain apiFilterChain(
            final HttpSecurity http,
            final JwtTokenService jwtTokenService,
            final RestAuthenticationEntryPoint restAuthenticationEntryPoint,
            final RestAccessDeniedHandler restAccessDeniedHandler
    ) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(restAuthenticationEntryPoint)
                        .accessDeniedHandler(restAccessDeniedHandler)
                )
                .authorizeHttpRequests(auth -> auth
                        // 정적 리소스 & 인프라
                        .requestMatchers("/", "/api/swagger-ui.html", "/api/swagger-ui/**",
                                "/api/api-docs/**", "/ws/**","/api/images/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**",
                                "/actuator/info", "/actuator/prometheus").permitAll()
                        // 인증 관련 (로그아웃 제외 — 로그아웃은 인증 필요)
                        .requestMatchers("/api/v1/auth/signup", "/api/v1/auth/login",
                                "/api/v1/auth/refresh", "/api/v1/auth/oauth2/token",
                                "/api/v1/auth/social/urls").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/performances/*/seats/status").authenticated()
                        // 읽기 전용 공개 API (GET만)
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/shows/**",
                                "/api/v1/performances/**",
                                "/api/v1/genres/**",
                                "/api/v1/meta/**"
                        ).permitAll()
                        // 나머지 전부 인증 필수
                        .anyRequest().authenticated()
                );

        http.addFilterBefore(new JwtAuthenticationFilter(jwtTokenService), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(final CorsProperties corsProperties) {
        final CorsConfiguration corsConfiguration = new CorsConfiguration();
        corsConfiguration.setAllowedOrigins(corsProperties.getAllowedOrigins());
        corsConfiguration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        corsConfiguration.setAllowedHeaders(List.of("*"));
        corsConfiguration.setExposedHeaders(List.of(HttpHeaders.AUTHORIZATION));
        corsConfiguration.setAllowCredentials(true);
        corsConfiguration.setMaxAge(3600L);

        final UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", corsConfiguration);
        source.registerCorsConfiguration("/ws/**", corsConfiguration);
        source.registerCorsConfiguration(OAuth2EndpointConstants.AUTHORIZATION_BASE_URI + "/**", corsConfiguration);
        source.registerCorsConfiguration(OAuth2EndpointConstants.CALLBACK_BASE_URI_PATTERN, corsConfiguration);
        return source;
    }
}
