package com.ticket.venue.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import com.ticket.shared.config.CacheConfig;
import com.ticket.venue.api.VenueSeatSnapshot;
import com.ticket.venue.domain.Seat;
import com.ticket.venue.domain.SeatRepository;

/** 주문마다 읽던 좌석 라벨을 공연장 좌석 전체 한 번 읽기로 바꿨는지 본다. 같은 공연장의 다른 좌석도 다시 읽지 않아야 한다. */
@SuppressWarnings("NonAsciiCharacters")
@SpringJUnitConfig({CacheConfig.class, VenueSeatReader.class, SeatLookupService.class})
class VenueSeatReaderCacheTest {
    @MockitoBean
    private SeatRepository seatRepository;

    @Autowired
    private SeatLookupService seatLookupService;

    @Test
    void 같은_공연장의_좌석은_공연장_좌석을_한_번만_읽고_고른다() {
        final List<Seat> seats = List.of(seat(10L, "A"), seat(11L, "B"));
        when(seatRepository.findAllByVenueId(3L)).thenReturn(seats);

        final List<VenueSeatSnapshot> first = seatLookupService.findSeats(3L, Set.of(10L));
        final List<VenueSeatSnapshot> second = seatLookupService.findSeats(3L, Set.of(11L, 99L));

        assertThat(first).extracting(VenueSeatSnapshot::section).containsExactly("A");
        // 이 공연장에 없는 좌석(99)은 빠진다.
        assertThat(second).extracting(VenueSeatSnapshot::section).containsExactly("B");
        verify(seatRepository, times(1)).findAllByVenueId(3L);
    }

    private Seat seat(final long id, final String section) {
        final Seat seat = mock(Seat.class);
        when(seat.getId()).thenReturn(id);
        when(seat.getFloor()).thenReturn(1);
        when(seat.getSection()).thenReturn(section);
        when(seat.getRowNo()).thenReturn("1");
        when(seat.getSeatNo()).thenReturn("1");
        when(seat.getX()).thenReturn(0.0);
        when(seat.getY()).thenReturn(0.0);
        return seat;
    }
}
