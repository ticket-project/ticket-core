package com.ticket.booking.seat.infrastructure;

import com.ticket.error.TicketException;
import com.ticket.member.AccessTokenAuthenticator;
import com.ticket.member.AuthenticatedMember;
import java.util.List;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * STOMP CONNECT 프레임에서 JWT 토큰을 추출하여 인증을 처리하는 인터셉터.
 * Spring Security의 WebSocket 보안보다 먼저 실행되도록 @Order 설정.
 * 인증 실패 시 연결을 차단합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE + 99)
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String AUTHORIZATION_HEADER = "Authorization";

    private final AccessTokenAuthenticator accessTokenAuthenticator;

    @Override
    public Message<?> preSend(final Message<?> message, final MessageChannel channel) {
        final StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            final String authorization = accessor.getFirstNativeHeader(AUTHORIZATION_HEADER);

            if (authorization != null && authorization.startsWith(BEARER_PREFIX)) {
                final String token = authorization.substring(BEARER_PREFIX.length());
                final AuthenticatedMember member;
                try {
                    member = accessTokenAuthenticator.authenticate(token);
                } catch (final TicketException exception) {
                    log.warn("웹소켓 JWT 인증에 실패해 연결을 차단합니다.");
                    throw new MessageDeliveryException("JWT 인증 실패");
                }
                accessor.setUser(new UsernamePasswordAuthenticationToken(
                        member,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + member.role()))
                ));
                log.info("웹소켓 인증에 성공했습니다. memberId={}", member.memberId());
            } else {
                log.warn("웹소켓 CONNECT 요청에 Authorization 헤더가 없어 연결을 차단합니다.");
                throw new MessageDeliveryException("인증 정보가 없습니다");
            }
        }

        return message;
    }
}
