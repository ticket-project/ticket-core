package com.ticket.booking.selection.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ticket.booking.exception.BookingErrorCode;
import com.ticket.booking.exception.BookingException;
import com.ticket.booking.seat.port.SeatStatusEvent;
import com.ticket.booking.seat.port.SeatStatusEvent.SeatStatusAction;
import com.ticket.booking.selection.domain.SeatSelectionStore.SelectResult;

@SuppressWarnings("NonAsciiCharacters")
@ExtendWith(MockitoExtension.class)
class SeatSelectionServiceTest {
    private static final SeatStatusEvent SELECTED =
            new SeatStatusEvent(10L, 30L, 20L, SeatStatusAction.SELECTED, LocalDateTime.of(2026, 3, 15, 10, 0));
    private static final SeatStatusEvent DESELECTED =
            new SeatStatusEvent(10L, 30L, 20L, SeatStatusAction.DESELECTED, LocalDateTime.of(2026, 3, 15, 10, 0));

    @Mock
    private SeatSelectionStore seatSelectionStore;

    @InjectMocks
    private SeatSelectionService seatSelectionService;

    @Test
    void 빈_좌석이면_선택한다() {
        // given
        when(seatSelectionStore.selectIfAbsent(10L, 20L, "3", Duration.ofMinutes(5), 4, SELECTED))
                .thenReturn(SelectResult.SELECTED);
        // when
        seatSelectionService.select(10L, 20L, 3L, 4, SELECTED);
        // then
        verify(seatSelectionStore).selectIfAbsent(10L, 20L, "3", Duration.ofMinutes(5), 4, SELECTED);
    }

    @Test
    void 이미_선택된_좌석이면_예외를_던진다() {
        // given
        when(seatSelectionStore.selectIfAbsent(10L, 20L, "3", Duration.ofMinutes(5), null, SELECTED))
                .thenReturn(SelectResult.ALREADY_SELECTED);
        // when
        // then
        assertThatThrownBy(() -> seatSelectionService.select(10L, 20L, 3L, null, SELECTED))
                .isInstanceOf(BookingException.class)
                .hasFieldOrPropertyWithValue("errorCode", BookingErrorCode.E4001);
    }

    @Test
    void 회원_선택_한도를_넘으면_선점_한도_초과로_거절한다() {
        when(seatSelectionStore.selectIfAbsent(10L, 20L, "3", Duration.ofMinutes(5), 4, SELECTED))
                .thenReturn(SelectResult.LIMIT_EXCEEDED);

        assertThatThrownBy(() -> seatSelectionService.select(10L, 20L, 3L, 4, SELECTED))
                .isInstanceOf(BookingException.class)
                .hasFieldOrPropertyWithValue("errorCode", BookingErrorCode.E6001);
    }

    @Test
    void 선택한_정보가_없으면_해제를_건너뛴다() {
        // given
        when(seatSelectionStore.getHolder(10L, 20L)).thenReturn(null);
        // when
        assertThat(seatSelectionService.deselect(10L, 20L, 3L, DESELECTED)).isFalse();
        // then
        verify(seatSelectionStore).releaseIfOwned(10L, 20L, "3", DESELECTED);
    }

    @Test
    void 다른_회원이_선택한_좌석은_해제할_수_없다() {
        // given
        when(seatSelectionStore.getHolder(10L, 20L)).thenReturn("4");
        // when
        // then
        assertThatThrownBy(() -> seatSelectionService.deselect(10L, 20L, 3L, DESELECTED))
                .isInstanceOf(BookingException.class)
                .hasFieldOrPropertyWithValue("errorCode", BookingErrorCode.E4002);
    }

    @Test
    void 본인이_선택한_좌석은_해제한다() {
        // given
        when(seatSelectionStore.releaseIfOwned(10L, 20L, "3", DESELECTED)).thenReturn(true);
        // when
        assertThat(seatSelectionService.deselect(10L, 20L, 3L, DESELECTED)).isTrue();
        // then
        verify(seatSelectionStore).releaseIfOwned(10L, 20L, "3", DESELECTED);
        verify(seatSelectionStore, never()).getHolder(10L, 20L);
    }

    @Test
    void 비동기_정리는_현재_소유자가_같을_때만_원자적으로_해제한다() {
        when(seatSelectionStore.releaseIfOwned(10L, 20L, "3", DESELECTED)).thenReturn(true);

        boolean released = seatSelectionService.deselectIfOwned(10L, 20L, 3L, DESELECTED);

        assertThat(released).isTrue();
        verify(seatSelectionStore).releaseIfOwned(10L, 20L, "3", DESELECTED);
    }

    @Test
    void 해제_시점에_다른_회원이_점유중이면_예외를_던진다() {
        // given
        when(seatSelectionStore.getHolder(10L, 20L)).thenReturn("4");
        when(seatSelectionStore.releaseIfOwned(10L, 20L, "3", DESELECTED)).thenReturn(false);
        // when
        // then
        assertThatThrownBy(() -> seatSelectionService.deselect(10L, 20L, 3L, DESELECTED))
                .isInstanceOf(BookingException.class)
                .hasFieldOrPropertyWithValue("errorCode", BookingErrorCode.E4002);
    }

    @Test
    void 주문할_좌석을_모두_선택_중이면_통과하고_만료_기록은_읽지_않는다() {
        when(seatSelectionStore.getSelectedSeatIdsByMember(10L, "3")).thenReturn(Set.of(20L, 21L));

        seatSelectionService.requireSelectedBy(10L, 3L, List.of(20L, 21L));

        verify(seatSelectionStore, never()).getRecentlyExpiredSeatIdsByMember(10L, "3");
    }

    @Test
    void 빠진_좌석이_모두_최근에_만료됐으면_선택_시간_만료로_거절한다() {
        when(seatSelectionStore.getSelectedSeatIdsByMember(10L, "3")).thenReturn(Set.of(20L));
        when(seatSelectionStore.getRecentlyExpiredSeatIdsByMember(10L, "3")).thenReturn(Set.of(21L));

        assertThatThrownBy(() -> seatSelectionService.requireSelectedBy(10L, 3L, List.of(20L, 21L)))
                .isInstanceOf(BookingException.class)
                .hasFieldOrPropertyWithValue("errorCode", BookingErrorCode.E4007);
    }

    @Test
    void 선택한_적_없는_좌석이_섞여_있으면_선택하지_않은_좌석으로_거절한다() {
        when(seatSelectionStore.getSelectedSeatIdsByMember(10L, "3")).thenReturn(Set.of());
        when(seatSelectionStore.getRecentlyExpiredSeatIdsByMember(10L, "3")).thenReturn(Set.of(20L));

        assertThatThrownBy(() -> seatSelectionService.requireSelectedBy(10L, 3L, List.of(20L, 21L)))
                .isInstanceOf(BookingException.class)
                .hasFieldOrPropertyWithValue("errorCode", BookingErrorCode.E4006);
    }

    /** 선점 확인은 선택 스크립트 안에서 선택과 같은 순간에 한다. 그래서 좌석 락 없이도 선택과 선점이 한 좌석에 함께 생기지 않는다. */
    @Test
    void 선점된_좌석이면_선점_충돌로_거절한다() {
        when(seatSelectionStore.selectIfAbsent(10L, 20L, "3", Duration.ofMinutes(5), 4, SELECTED))
                .thenReturn(SelectResult.HELD);

        assertThatThrownBy(() -> seatSelectionService.select(10L, 20L, 3L, 4, SELECTED))
                .isInstanceOf(BookingException.class)
                .hasFieldOrPropertyWithValue("errorCode", BookingErrorCode.E6000);
    }

    @Test
    void 만료_알림은_비어_있을_때만_발행하도록_저장소에_맡긴다() {
        seatSelectionService.notifyIfFree(10L, 20L, DESELECTED);

        verify(seatSelectionStore).publishIfFree(10L, 20L, DESELECTED);
    }
}
