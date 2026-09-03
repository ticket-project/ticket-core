package com.ticket.core.config;

import com.ticket.booking.internal.infrastructure.websocket.WebSocketAuthInterceptor;
import com.ticket.core.config.security.CorsProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
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
