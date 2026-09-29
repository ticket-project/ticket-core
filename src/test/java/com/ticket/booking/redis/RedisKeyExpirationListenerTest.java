package com.ticket.booking.redis;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.Message;

import com.ticket.booking.selection.persistence.SeatSelectionRedisKey;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class RedisKeyExpirationListenerTest {
    private static final String SELECT_KEY = SeatSelectionRedisKey.select(10L, 20L);

    @Mock
    private RedisKeyExpirationHandler firstHandler;

    @Mock
    private RedisKeyExpirationHandler secondHandler;

    @Test
    void 만료_키를_처리할_수_있는_첫번째_핸들러에만_위임한다() {
        RedisKeyExpirationListener listener = new RedisKeyExpirationListener(List.of(firstHandler, secondHandler));

        when(firstHandler.canHandle(SELECT_KEY)).thenReturn(true);

        listener.onMessage(message(SELECT_KEY), new byte[0]);

        verify(firstHandler).canHandle(SELECT_KEY);
        verify(firstHandler).handle(SELECT_KEY);
        verify(secondHandler, never()).canHandle(anyString());
        verifyNoInteractions(secondHandler);
    }

    @Test
    void 지원하는_핸들러가_없으면_아무_처리도_하지_않는다() {
        RedisKeyExpirationListener listener = new RedisKeyExpirationListener(List.of(firstHandler, secondHandler));

        when(firstHandler.canHandle("unknown:key")).thenReturn(false);
        when(secondHandler.canHandle("unknown:key")).thenReturn(false);

        listener.onMessage(message("unknown:key"), new byte[0]);

        verify(firstHandler).canHandle("unknown:key");
        verify(secondHandler).canHandle("unknown:key");
        verify(firstHandler, never()).handle(anyString());
        verify(secondHandler, never()).handle(anyString());
    }

    private Message message(final String body) {
        return new Message() {
            @Override
            public byte[] getBody() {
                return body.getBytes(StandardCharsets.UTF_8);
            }

            @Override
            public byte[] getChannel() {
                return null;
            }
        };
    }
}
