package com.ticket.booking.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import com.ticket.booking.admission.BookingEntryGuard;
import com.ticket.booking.exception.AdmissionErrorCode;
import com.ticket.booking.exception.AdmissionTokenException;
import com.ticket.member.api.AuthenticatedMember;
import com.ticket.security.api.AccessTokenAuthenticationApi;
import com.ticket.security.exception.AuthException;
import com.ticket.security.exception.SecurityErrorCode;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class WebSocketAuthInterceptorTest {
    @Mock
    private AccessTokenAuthenticationApi accessTokenAuthenticationApi;

    @Mock
    private BookingEntryGuard bookingEntryGuard;

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 9, 20, 0);
    private final Clock clock = Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);

    private WebSocketAuthInterceptor interceptor() {
        return new WebSocketAuthInterceptor(accessTokenAuthenticationApi, bookingEntryGuard, clock);
    }

    private final MessageChannel channel = mock(MessageChannel.class);

    @Test
    void 유효한_bearer_토큰이면_인증된_사용자를_STOMP_세션에_설정한다() {
        final AuthenticatedMember member = new AuthenticatedMember(1L, "MEMBER");
        when(accessTokenAuthenticationApi.authenticate("valid-token")).thenReturn(member);

        final StompHeaderAccessor accessor = connectAccessor("Bearer valid-token");
        final Message<?> message = interceptor().preSend(toMessage(accessor), channel);

        final StompHeaderAccessor result = StompHeaderAccessor.wrap(message);
        assertThat(result.getUser()).isInstanceOf(UsernamePasswordAuthenticationToken.class);
        assertThat(((UsernamePasswordAuthenticationToken) result.getUser()).getPrincipal())
                .isEqualTo(member);
        verify(accessTokenAuthenticationApi).authenticate("valid-token");
    }

    @Test
    void authorization_헤더가_없으면_연결을_차단한다() {
        final StompHeaderAccessor accessor = connectAccessor(null);

        assertThatThrownBy(() -> interceptor().preSend(toMessage(accessor), channel))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void 토큰_검증에_실패하면_연결을_차단한다() {
        when(accessTokenAuthenticationApi.authenticate("bad-token"))
                .thenThrow(new AuthException(SecurityErrorCode.E1000));

        final StompHeaderAccessor accessor = connectAccessor("Bearer bad-token");

        assertThatThrownBy(() -> interceptor().preSend(toMessage(accessor), channel))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void CONNECT가_아닌_프레임은_그대로_통과한다() {
        final StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);

        final Message<?> message = interceptor().preSend(toMessage(accessor), channel);

        assertThat(message).isNotNull();
    }

    @Test
    void 묶음_주소_구독은_세션_회원과_입장권으로_예매_진입_검사를_한다() {
        final StompHeaderAccessor accessor =
                subscribeAccessor("/topic/performance/10/seats/batch", MEMBER, "admission-token");

        final Message<?> message = interceptor().preSend(toMessage(accessor), channel);

        assertThat(message).isNotNull();
        verify(bookingEntryGuard).check(10L, 1L, "admission-token", NOW);
    }

    @Test
    void 예매_진입_검사에_실패하면_묶음_주소_구독을_차단한다() {
        when(bookingEntryGuard.check(any(), any(), any(), any()))
                .thenThrow(new AdmissionTokenException(AdmissionErrorCode.E8000, "missing"));
        final StompHeaderAccessor accessor = subscribeAccessor("/topic/performance/10/seats/batch", MEMBER, null);

        assertThatThrownBy(() -> interceptor().preSend(toMessage(accessor), channel))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void 인증된_세션이_아니면_묶음_주소_구독을_차단한다() {
        final StompHeaderAccessor accessor = subscribeAccessor("/topic/performance/10/seats/batch", null, null);

        assertThatThrownBy(() -> interceptor().preSend(toMessage(accessor), channel))
                .isInstanceOf(MessageDeliveryException.class);
        verifyNoInteractions(bookingEntryGuard);
    }

    @Test
    void 옛_좌석_주소_구독은_입장권을_검사하지_않는다() {
        final StompHeaderAccessor accessor = subscribeAccessor("/topic/performance/10/seats", MEMBER, null);

        final Message<?> message = interceptor().preSend(toMessage(accessor), channel);

        assertThat(message).isNotNull();
        verifyNoInteractions(bookingEntryGuard);
    }

    private static final AuthenticatedMember MEMBER = new AuthenticatedMember(1L, "MEMBER");

    private static StompHeaderAccessor subscribeAccessor(
            final String destination,
            final @Nullable AuthenticatedMember member,
            final @Nullable String admissionToken) {
        final StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setLeaveMutable(true);
        accessor.setSessionId("session-1");
        accessor.setDestination(destination);
        if (member != null) {
            accessor.setUser(new UsernamePasswordAuthenticationToken(
                    member, null, List.of(new SimpleGrantedAuthority("ROLE_" + member.role()))));
        }
        if (admissionToken != null) {
            accessor.addNativeHeader("X-Admission-Token", admissionToken);
        }
        return accessor;
    }

    private static StompHeaderAccessor connectAccessor(final String authorizationHeader) {
        final StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setLeaveMutable(true);
        accessor.setSessionId("session-1");
        if (authorizationHeader != null) {
            accessor.addNativeHeader("Authorization", authorizationHeader);
        }
        return accessor;
    }

    private static Message<?> toMessage(final StompHeaderAccessor accessor) {
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
