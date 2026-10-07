package com.ticket.booking.selection.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ticket.booking.exception.BookingErrorCode;
import com.ticket.booking.exception.BookingException;
import com.ticket.booking.selection.domain.SeatSelectionStore.SelectResult;

@SuppressWarnings("NonAsciiCharacters")
@ExtendWith(MockitoExtension.class)
class SeatSelectionServiceTest {
    @Mock
    private SeatSelectionStore seatSelectionStore;

    @InjectMocks
    private SeatSelectionService seatSelectionService;

    @Test
    void 빈_좌석이면_선택한다() {
        // given
        when(seatSelectionStore.selectIfAbsent(10L, 20L, "3", Duration.ofMinutes(5), 4))
                .thenReturn(SelectResult.SELECTED);
        // when
        seatSelectionService.select(10L, 20L, 3L, 4);
        // then
        verify(seatSelectionStore).selectIfAbsent(10L, 20L, "3", Duration.ofMinutes(5), 4);
    }

    @Test
    void 이미_선택된_좌석이면_예외를_던진다() {
        // given
        when(seatSelectionStore.selectIfAbsent(10L, 20L, "3", Duration.ofMinutes(5), null))
                .thenReturn(SelectResult.ALREADY_SELECTED);
        // when
        // then
        assertThatThrownBy(() -> seatSelectionService.select(10L, 20L, 3L, null))
                .isInstanceOf(BookingException.class)
                .hasFieldOrPropertyWithValue("errorCode", BookingErrorCode.E4001);
    }

    @Test
    void 회원_선택_한도를_넘으면_선점_한도_초과로_거절한다() {
        when(seatSelectionStore.selectIfAbsent(10L, 20L, "3", Duration.ofMinutes(5), 4))
                .thenReturn(SelectResult.LIMIT_EXCEEDED);

        assertThatThrownBy(() -> seatSelectionService.select(10L, 20L, 3L, 4))
                .isInstanceOf(BookingException.class)
                .hasFieldOrPropertyWithValue("errorCode", BookingErrorCode.E6001);
    }

    @Test
    void 선택한_정보가_없으면_해제를_건너뛴다() {
        // given
        when(seatSelectionStore.getHolder(10L, 20L)).thenReturn(null);
        // when
        assertThat(seatSelectionService.deselect(10L, 20L, 3L)).isFalse();
        // then
        verify(seatSelectionStore).releaseIfOwned(10L, 20L, "3");
    }

    @Test
    void 다른_회원이_선택한_좌석은_해제할_수_없다() {
        // given
        when(seatSelectionStore.getHolder(10L, 20L)).thenReturn("4");
        // when
        // then
        assertThatThrownBy(() -> seatSelectionService.deselect(10L, 20L, 3L))
                .isInstanceOf(BookingException.class)
                .hasFieldOrPropertyWithValue("errorCode", BookingErrorCode.E4002);
    }

    @Test
    void 본인이_선택한_좌석은_해제한다() {
        // given
        when(seatSelectionStore.releaseIfOwned(10L, 20L, "3")).thenReturn(true);
        // when
        assertThat(seatSelectionService.deselect(10L, 20L, 3L)).isTrue();
        // then
        verify(seatSelectionStore).releaseIfOwned(10L, 20L, "3");
        verify(seatSelectionStore, never()).getHolder(10L, 20L);
    }

    @Test
    void 비동기_정리는_현재_소유자가_같을_때만_원자적으로_해제한다() {
        when(seatSelectionStore.releaseIfOwned(10L, 20L, "3")).thenReturn(true);

        boolean released = seatSelectionService.deselectIfOwned(10L, 20L, 3L);

        assertThat(released).isTrue();
        verify(seatSelectionStore).releaseIfOwned(10L, 20L, "3");
    }

    @Test
    void 해제_시점에_다른_회원이_점유중이면_예외를_던진다() {
        // given
        when(seatSelectionStore.getHolder(10L, 20L)).thenReturn("4");
        when(seatSelectionStore.releaseIfOwned(10L, 20L, "3")).thenReturn(false);
        // when
        // then
        assertThatThrownBy(() -> seatSelectionService.deselect(10L, 20L, 3L))
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

    @Test
    void 본인이_선택한_좌석만_일괄_해제한다() {
        // given
        when(seatSelectionStore.releaseAllByMember(10L, "3")).thenReturn(List.of(20L));
        // then
        assertThat(seatSelectionService.deselectAll(10L, 3L)).containsExactly(20L);
    }
}
