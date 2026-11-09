package com.ticket.security.http;

import java.util.List;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.config.observation.SecurityObservationSettings;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.ticket.security.jwt.JwtAccessTokenCodec;
import com.ticket.shared.config.CorsProperties;

@Configuration
@EnableConfigurationProperties(CorsProperties.class)
public class ApiSecurityConfig {
    @Bean
    @Order(2)
    public SecurityFilterChain apiFilterChain(
            final HttpSecurity http,
            final JwtAccessTokenCodec accessTokenReader,
            final RestAuthenticationEntryPoint restAuthenticationEntryPoint)
            throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exception -> exception.authenticationEntryPoint(restAuthenticationEntryPoint))
                .authorizeHttpRequests(auth -> auth.requestMatchers(
                                "/",
                                "/api/swagger-ui.html",
                                "/api/swagger-ui/**",
                                "/api/api-docs/**",
                                "/ws/**",
                                "/api/images/**")
                        .permitAll()
                        .requestMatchers(
                                "/actuator/health", "/actuator/health/**", "/actuator/info", "/actuator/prometheus")
                        .permitAll()
                        .requestMatchers(
                                "/api/v1/auth/refresh", "/api/v1/auth/oauth2/token", "/api/v1/auth/social/urls")
                        .permitAll()
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/performances/*/seats/status",
                                "/api/v1/performances/*/seats/occupied",
                                "/api/v1/performances/*/seats/occupancy")
                        .authenticated()
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/shows/**",
                                "/api/v1/performances/**",
                                "/api/v1/booking/performances/*/booking-mode",
                                "/api/v1/genres/**",
                                "/api/v1/meta/**")
                        .permitAll()
                        .anyRequest()
                        .authenticated());

        http.addFilterBefore(
                new AccessTokenAuthenticationFilter(accessTokenReader), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /**
     * 보안 필터 묶음·인증·권한 확인 계측({@code spring.security.*})을 만들지 않는다. 요청마다 필터마다 계측 포장이 붙지만 읽는 곳이 없다. 요청 시간
     * 계측({@code http.server.requests})은 Spring MVC가 따로 남기므로 그대로다.
     * {@code management.observations.enable.spring.security=false}는 계측을 버릴 뿐 필터
     * 포장({@code ObservationFilterChainDecorator})은 남겨서 이 bean을 쓴다.
     */
    @Bean
    public SecurityObservationSettings securityObservationSettings() {
        return SecurityObservationSettings.noObservations();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(final CorsProperties corsProperties) {
        final CorsConfiguration corsConfiguration = new CorsConfiguration();
        corsConfiguration.setAllowedOrigins(corsProperties.allowedOrigins());
        corsConfiguration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        corsConfiguration.setAllowedHeaders(List.of("*"));
        corsConfiguration.setAllowCredentials(true);
        corsConfiguration.setMaxAge(3600L);

        final UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", corsConfiguration);
        source.registerCorsConfiguration("/ws/**", corsConfiguration);
        return source;
    }
}
