package com.ticket.booking.selection.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ticket.booking.selection.usecase.SeatSelectionCoordinator;

/** 이 핸들러는 Redis key 해석과 호출만 한다. 현재 상태 확인과 알림 필요 여부 판단은 {@code SeatSelectionCoordinatorTest}가 고정한다. */
@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class SeatSelectionExpirationHandlerTest {
    @Mock
    private SeatSelectionCoordinator seatSelectionCoordinator;

    @InjectMocks
    private SeatSelectionExpirationHandler handler;

    @Test
    void 좌석_select_키를_해석해_application에_넘긴다() {
        String expiredKey = SeatSelectionRedisKey.select(10L, 20L);

        assertThat(handler.supports(expiredKey)).isTrue();

        handler.handle(expiredKey);

        verify(seatSelectionCoordinator).notifyReleasedIfFree(10L, 20L);
    }

    @Test
    void 좌석_select_키가_아니면_지원하지_않는다() {
        assertThat(handler.supports("unknown:key")).isFalse();
        verifyNoInteractions(seatSelectionCoordinator);
    }
}
