package com.ticket.booking.websocket;

import java.security.Principal;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.WebSocketExtension;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.server.standard.StandardWebSocketUpgradeStrategy;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import com.ticket.shared.config.CorsProperties;

import lombok.RequiredArgsConstructor;

/**
 * WebSocket STOMP 설정을 소유 module이 직접 한다.
 *
 * <p>이 앱에서 WebSocket을 쓰는 module은 booking 하나다 — 좌석 상태 변경을 {@code /topic}으로 발행하고, STOMP CONNECT 시
 * {@link WebSocketAuthInterceptor}로 인증한다. 그래서 브로커 활성화 ({@code @EnableWebSocketMessageBroker})까지 booking이 갖는다. 전역 설정
 * module이 이 인터셉터를 등록해 주면 그 방향의 참조를 열기 위해 {@code @NamedInterface}가 필요해진다.
 *
 * <p>두 번째 module이 WebSocket을 쓰기 시작하면 그때 활성화만 떼어낸다 — {@code @EnableWebSocketMessageBroker}는 앱 전체에 한 곳만 있어야 하지만
 * {@link WebSocketMessageBrokerConfigurer} 구현은 Spring이 여러 개를 모아 순서대로 적용한다.
 *
 * <p>{@link CorsProperties}는 {@code shared}의 값 홀더이므로 이 module도 직접 {@code @EnableConfigurationProperties}로
 * 등록한다(security의 {@code ApiSecurityConfig}도 같은 값을 등록해 쓴다 — 같은 {@code @ConfigurationProperties} 타입을 두 곳에서 등록해도 bean은
 * 하나다). 그래야 booking이 STANDALONE으로 뜰 때 member 없이도 endpoint 허용 origin을 읽을 수 있다.
 */
@Configuration
@EnableWebSocketMessageBroker
@EnableConfigurationProperties(CorsProperties.class)
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    private final WebSocketAuthInterceptor webSocketAuthInterceptor;
    private final CorsProperties corsProperties;

    @Override
    public void configureMessageBroker(final MessageBrokerRegistry registry) {
        // 서버 → 클라이언트 브로드캐스트 prefix (좌석 상태 변경 알림용)
        registry.enableSimpleBroker("/topic");
        registry.setPreservePublishOrder(true);
    }

    @Override
    public void registerStompEndpoints(final StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setHandshakeHandler(new DefaultHandshakeHandler(new NoCompressionUpgradeStrategy()))
                .setAllowedOrigins(corsProperties.allowedOrigins().toArray(String[]::new))
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(final ChannelRegistration registration) {
        // STOMP CONNECT 시 JWT 인증 처리
        registration.interceptors(webSocketAuthInterceptor);
    }

    /**
     * WebSocket 메시지 압축(permessage-deflate)을 받아들이지 않는 upgrade. 같은 좌석 알림을 구독자마다 따로 압축해 CPU 5.7%를 썼다(2026-11-09 프로파일,
     * docs/plans/core-cpu-per-user.md 5). 압축은 클라이언트가 제안하고 서버가 고르는 선택 확장이라 FE는 압축 없이 그대로 동작한다. 대가로 알림 전송량이 5~10배가 된다.
     *
     * <p><b>왜 요청 헤더를 숨기나.</b> {@code DefaultHandshakeHandler}가 고른 확장을 빈 목록으로 넘겨도 Tomcat은 "앱이 확장을 지정하지 않음"으로 보고 자기 기본
     * 확장(permessage-deflate)으로 요청 헤더와 다시 협상한다. 그래서 Tomcat에 넘기는 요청에서 {@code Sec-WebSocket-Extensions}를 지운다.
     *
     * <p>{@code withSockJS()} 앞에 등록하면 SockJS의 websocket transport({@code /ws/{server}/{session}/websocket})와 raw
     * WebSocket({@code /ws/websocket}, 측정 시나리오가 쓴다)이 모두 이 upgrade를 쓴다. SockJS의 나머지 transport(xhr 등)는 WebSocket이 아니라 해당이
     * 없다.
     */
    static class NoCompressionUpgradeStrategy extends StandardWebSocketUpgradeStrategy {
        private static final String EXTENSIONS_HEADER = "Sec-WebSocket-Extensions";

        @Override
        public List<WebSocketExtension> getSupportedExtensions(final ServerHttpRequest request) {
            return List.of();
        }

        @Override
        public void upgrade(
                final ServerHttpRequest request,
                final ServerHttpResponse response,
                final @Nullable String selectedProtocol,
                final List<WebSocketExtension> selectedExtensions,
                final @Nullable Principal user,
                final WebSocketHandler wsHandler,
                final Map<String, Object> attrs) {
            final HttpServletRequest servletRequest = getHttpServletRequest(request);
            final HttpServletRequest withoutExtensions = new HttpServletRequestWrapper(servletRequest) {
                @Override
                public @Nullable String getHeader(final String name) {
                    return EXTENSIONS_HEADER.equalsIgnoreCase(name) ? null : super.getHeader(name);
                }

                @Override
                public Enumeration<String> getHeaders(final String name) {
                    return EXTENSIONS_HEADER.equalsIgnoreCase(name)
                            ? Collections.emptyEnumeration()
                            : super.getHeaders(name);
                }
            };
            super.upgrade(
                    new ServletServerHttpRequest(withoutExtensions),
                    response,
                    selectedProtocol,
                    selectedExtensions,
                    user,
                    wsHandler,
                    attrs);
        }
    }
}
