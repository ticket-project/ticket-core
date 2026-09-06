package com.ticket.booking.infrastructure.websocket;

import com.ticket.shared.CorsProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket STOMP 배선을 소유 module이 직접 한다.
 *
 * <p>이 앱에서 WebSocket을 쓰는 module은 booking 하나다 — 좌석 상태 변경을 {@code /topic}으로
 * 발행하고, STOMP CONNECT 시 {@link WebSocketAuthInterceptor}로 인증한다. 그래서 브로커 활성화
 * ({@code @EnableWebSocketMessageBroker})까지 booking이 갖는다. 전역 설정 module이 이 인터셉터를
 * 등록해 주면 그 방향의 참조를 열기 위해 {@code @NamedInterface}가 필요해진다.
 *
 * <p>두 번째 module이 WebSocket을 쓰기 시작하면 그때 활성화만 떼어낸다 —
 * {@code @EnableWebSocketMessageBroker}는 앱 전체에 한 곳만 있어야 하지만
 * {@link WebSocketMessageBrokerConfigurer} 구현은 Spring이 여러 개를 모아 순서대로 적용한다.
 *
 * <p>{@link CorsProperties}는 {@code shared}의 값 홀더이므로 이 module도 직접
 * {@code @EnableConfigurationProperties}로 등록한다(member의 {@code SecurityConfig}도 같은 값을
 * 등록해 쓴다 — 같은 {@code @ConfigurationProperties} 타입을 두 곳에서 등록해도 bean은 하나다).
 * 그래야 booking이 STANDALONE으로 뜰 때 member 없이도 endpoint 허용 origin을 읽을 수 있다.
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
                .setAllowedOrigins(corsProperties.getAllowedOrigins().toArray(String[]::new))
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(final ChannelRegistration registration) {
        // STOMP CONNECT 시 JWT 인증 처리
        registration.interceptors(webSocketAuthInterceptor);
    }
}
