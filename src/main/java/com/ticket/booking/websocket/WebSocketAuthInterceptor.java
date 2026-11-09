package com.ticket.booking.websocket;

import java.security.Principal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;
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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import com.ticket.booking.admission.BookingEntryGuard;
import com.ticket.member.api.AuthenticatedMember;
import com.ticket.security.api.AccessTokenAuthenticationApi;
import com.ticket.shared.exception.TicketException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * STOMP 프레임 인증·인가 인터셉터.
 *
 * <ul>
 *   <li>CONNECT: {@code Authorization: Bearer} JWT로 회원을 인증해 세션 사용자로 둔다. 실패하면 연결을 차단한다.
 *   <li>SUBSCRIBE {@code /topic/performance/{performanceId}/seats/batch}: 세션 사용자와 {@code X-Admission-Token} 헤더로
 *       {@link BookingEntryGuard}를 통과해야 구독할 수 있다(HTTP 좌석 API와 같은 예매 진입 검사). 옛 주소 {@code .../seats}는 운영 FE가 입장권 없이 구독하므로
 *       검사하지 않는다(docs/architecture.md "프론트엔드 호환").
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE + 99)
public class WebSocketAuthInterceptor implements ChannelInterceptor {
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String ADMISSION_TOKEN_HEADER = "X-Admission-Token";
    private static final Pattern SEAT_BATCH_TOPIC = Pattern.compile("/topic/performance/(\\d+)/seats/batch");

    private final AccessTokenAuthenticationApi accessTokenAuthenticationApi;
    private final BookingEntryGuard bookingEntryGuard;
    private final Clock clock;

    @Override
    public Message<?> preSend(final Message<?> message, final MessageChannel channel) {
        final StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            final String authorization = accessor.getFirstNativeHeader(AUTHORIZATION_HEADER);

            if (authorization != null && authorization.startsWith(BEARER_PREFIX)) {
                final String token = authorization.substring(BEARER_PREFIX.length());
                final AuthenticatedMember member;
                try {
                    member = accessTokenAuthenticationApi.authenticate(token);
                } catch (final TicketException exception) {
                    log.warn("웹소켓 JWT 인증에 실패해 연결을 차단합니다.");
                    throw new MessageDeliveryException("JWT 인증 실패");
                }
                accessor.setUser(new UsernamePasswordAuthenticationToken(
                        member, null, List.of(new SimpleGrantedAuthority("ROLE_" + member.role()))));
                log.info("웹소켓 인증에 성공했습니다. memberId={}", member.memberId());
            } else {
                log.warn("웹소켓 CONNECT 요청에 Authorization 헤더가 없어 연결을 차단합니다.");
                throw new MessageDeliveryException("인증 정보가 없습니다");
            }
        }

        if (accessor != null && StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            checkSeatBatchSubscription(accessor);
        }

        return message;
    }

    private void checkSeatBatchSubscription(final StompHeaderAccessor accessor) {
        final String destination = accessor.getDestination();
        if (destination == null) {
            return;
        }
        final Matcher matcher = SEAT_BATCH_TOPIC.matcher(destination);
        if (!matcher.matches()) {
            return;
        }
        final AuthenticatedMember member = authenticatedMember(accessor.getUser());
        if (member == null) {
            log.warn("인증되지 않은 세션의 좌석 묶음 구독을 차단합니다. destination={}", destination);
            throw new MessageDeliveryException("인증 정보가 없습니다");
        }
        final Long performanceId = Long.valueOf(matcher.group(1));
        try {
            bookingEntryGuard.check(
                    performanceId,
                    member.memberId(),
                    accessor.getFirstNativeHeader(ADMISSION_TOKEN_HEADER),
                    LocalDateTime.now(clock));
        } catch (final TicketException exception) {
            log.warn("예매 진입 검사에 실패해 좌석 묶음 구독을 차단합니다. performanceId={}, memberId={}", performanceId, member.memberId());
            throw new MessageDeliveryException("예매 진입 검사 실패");
        }
    }

    private static @Nullable AuthenticatedMember authenticatedMember(final @Nullable Principal user) {
        if (user instanceof UsernamePasswordAuthenticationToken authentication
                && authentication.getPrincipal() instanceof AuthenticatedMember member) {
            return member;
        }
        return null;
    }
}
