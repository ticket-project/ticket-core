package com.ticket.booking.websocket;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;

import com.ticket.shared.api.CorsProperties;

class WebSocketConfigTest {
    @Test
    void server_events_are_published_to_each_session_in_order() {
        final MessageBrokerRegistry registry = mock(MessageBrokerRegistry.class);
        final WebSocketConfig config =
                new WebSocketConfig(
                        mock(WebSocketAuthInterceptor.class), mock(CorsProperties.class));

        config.configureMessageBroker(registry);

        verify(registry).enableSimpleBroker("/topic");
        verify(registry).setPreservePublishOrder(true);
    }
}
