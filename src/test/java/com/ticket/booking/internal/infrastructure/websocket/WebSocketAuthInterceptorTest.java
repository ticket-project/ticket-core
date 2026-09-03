package com.ticket.booking.internal.infrastructure.websocket;

import com.ticket.identity.AccessTokenAuthenticator;
import com.ticket.identity.AuthenticatedMember;
import com.ticket.identity.internal.exception.UnauthenticatedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class WebSocketAuthInterceptorTest {

    @Mock
    private AccessTokenAuthenticator accessTokenAuthenticator;

    @InjectMocks
    private WebSocketAuthInterceptor interceptor;

    private final MessageChannel channel = mock(MessageChannel.class);

    @Test
    void 유효한_bearer_토큰이면_인증된_사용자를_STOMP_세션에_설정한다() {
        final AuthenticatedMember member = new AuthenticatedMember(1L, "MEMBER");
        when(accessTokenAuthenticator.authenticate("valid-token")).thenReturn(member);

        final StompHeaderAccessor accessor = connectAccessor("Bearer valid-token");
        final Message<?> message = interceptor.preSend(toMessage(accessor), channel);

        final StompHeaderAccessor result = StompHeaderAccessor.wrap(message);
        assertThat(result.getUser()).isInstanceOf(UsernamePasswordAuthenticationToken.class);
        assertThat(((UsernamePasswordAuthenticationToken) result.getUser()).getPrincipal()).isEqualTo(member);
    }

    @Test
    void authorization_헤더가_없으면_연결을_차단한다() {
        final StompHeaderAccessor accessor = connectAccessor(null);

        assertThatThrownBy(() -> interceptor.preSend(toMessage(accessor), channel))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void 토큰_검증에_실패하면_연결을_차단한다() {
        when(accessTokenAuthenticator.authenticate("bad-token"))
                .thenThrow(new UnauthenticatedException());

        final StompHeaderAccessor accessor = connectAccessor("Bearer bad-token");

        assertThatThrownBy(() -> interceptor.preSend(toMessage(accessor), channel))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void CONNECT가_아닌_프레임은_그대로_통과한다() {
        final StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);

        final Message<?> message = interceptor.preSend(toMessage(accessor), channel);

        assertThat(message).isNotNull();
    }

    private static StompHeaderAccessor connectAccessor(final String authorizationHeader) {
        final StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setLeaveMutable(true);
        if (authorizationHeader != null) {
            accessor.addNativeHeader("Authorization", authorizationHeader);
        }
        return accessor;
    }

    private static Message<?> toMessage(final StompHeaderAccessor accessor) {
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
