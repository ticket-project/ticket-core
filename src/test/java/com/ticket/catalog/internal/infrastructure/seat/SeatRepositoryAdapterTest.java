package com.ticket.catalog.internal.infrastructure.seat;

import com.ticket.catalog.internal.domain.seat.Seat;
import com.ticket.catalog.internal.domain.seat.repository.SeatRepository;
import com.ticket.catalog.internal.domain.show.Region;
import com.ticket.catalog.internal.domain.show.Venue;
import com.ticket.core.infra.support.InfraReadRepositoryTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Import(SeatRepositoryAdapter.class)
@SuppressWarnings("NonAsciiCharacters")
class SeatRepositoryAdapterTest extends InfraReadRepositoryTestSupport {

    @Autowired
    private SeatRepository seatRepository;

    @Test
    void venue_id로_그_venue의_좌석만_조회한다() throws Exception {
        Venue venueA = persistVenue("venue-a", Region.SEOUL);
        Venue venueB = persistVenue("venue-b", Region.SEOUL);
        Seat seatInA1 = persistSeat(venueA, "가", "A", "1", 1);
        Seat seatInA2 = persistSeat(venueA, "가", "A", "2", 1);
        persistSeat(venueB, "가", "A", "1", 1);
        flushAndClear();

        List<Seat> result = seatRepository.findAllByVenueId(venueA.getId());

        assertThat(result).extracting(Seat::getId)
                .containsExactlyInAnyOrder(seatInA1.getId(), seatInA2.getId());
    }

    @Test
    void 좌석이_없는_venue는_빈_목록을_반환한다() throws Exception {
        Venue venue = persistVenue("empty-venue", Region.SEOUL);
        flushAndClear();

        List<Seat> result = seatRepository.findAllByVenueId(venue.getId());

        assertThat(result).isEmpty();
    }
}
