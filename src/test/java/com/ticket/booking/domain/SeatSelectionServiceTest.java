package com.ticket.booking.domain;

import com.ticket.booking.domain.SeatSelectionStore;
import com.ticket.booking.exception.SeatAlreadySelectedException;
import com.ticket.booking.exception.SeatNotOwnedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("NonAsciiCharacters")
@ExtendWith(MockitoExtension.class)
class SeatSelectionServiceTest {

    @Mock
    private SeatSelectionStore seatSelectionStore;

    @InjectMocks
    private SeatSelectionService seatSelectionService;

    @Test
    void 빈_좌석이면_선택한다() {
        //given
        when(seatSelectionStore.selectIfAbsent(10L, 20L, "3", java.time.Duration.ofMinutes(5))).thenReturn(true);

        //when
        seatSelectionService.select(10L, 20L, 3L);

        //then
        verify(seatSelectionStore).selectIfAbsent(10L, 20L, "3", java.time.Duration.ofMinutes(5));
    }

    @Test
    void 이미_선택된_좌석이면_예외를_던진다() {
        //given
        when(seatSelectionStore.selectIfAbsent(10L, 20L, "3", java.time.Duration.ofMinutes(5))).thenReturn(false);

        //when
        //then
        assertThatThrownBy(() -> seatSelectionService.select(10L, 20L, 3L))
                .isInstanceOf(SeatAlreadySelectedException.class);
    }

    @Test
    void 선택한_정보가_없으면_해제를_건너뛴다() {
        //given
        when(seatSelectionStore.getHolder(10L, 20L)).thenReturn(null);

        //when
        seatSelectionService.deselect(10L, 20L, 3L);

        //then
        verify(seatSelectionStore, never()).releaseIfOwned(10L, 20L, "3");
    }

    @Test
    void 다른_회원이_선택한_좌석은_해제할_수_없다() {
        //given
        when(seatSelectionStore.getHolder(10L, 20L)).thenReturn("4");

        //when
        //then
        assertThatThrownBy(() -> seatSelectionService.deselect(10L, 20L, 3L))
                .isInstanceOf(SeatNotOwnedException.class);
    }

    @Test
    void 본인이_선택한_좌석은_해제한다() {
        //given
        when(seatSelectionStore.getHolder(10L, 20L)).thenReturn("3");
        when(seatSelectionStore.releaseIfOwned(10L, 20L, "3")).thenReturn(true);

        //when
        seatSelectionService.deselect(10L, 20L, 3L);

        //then
        verify(seatSelectionStore).releaseIfOwned(10L, 20L, "3");
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
        //given
        when(seatSelectionStore.getHolder(10L, 20L)).thenReturn("3", "4");
        when(seatSelectionStore.releaseIfOwned(10L, 20L, "3")).thenReturn(false);

        //when
        //then
        assertThatThrownBy(() -> seatSelectionService.deselect(10L, 20L, 3L))
                .isInstanceOf(SeatNotOwnedException.class);
    }

    @Test
    void 본인이_선택한_좌석만_일괄_해제한다() {
        //given
        when(seatSelectionStore.releaseAllByMember(10L, "3")).thenReturn(List.of(20L));

        //then
        assertThat(seatSelectionService.deselectAll(10L, 3L).values()).containsExactly(20L);
    }
}

