package com.ticket.venue.query;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.ticket.testsupport.persistence.InfraReadRepositoryTestSupport;
import com.ticket.venue.api.Region;
import com.ticket.venue.api.VenueSeatAddress;
import com.ticket.venue.api.VenueSeatLayout;
import com.ticket.venue.api.VenueSeatLookupApi;
import com.ticket.venue.domain.Seat;
import com.ticket.venue.domain.Venue;

/**
 * venue 공개 계약({@link VenueSeatLookupApi})의 실제 DB 동작을 고정한다. 위임만 하던 {@code
 * VenueSeatLookupService}가 갖고 있던 빈 {@code seatIds} 처리도 이 조회로 왔다.
 */
@SuppressWarnings("NonAsciiCharacters")
class VenueSeatQueryTest extends InfraReadRepositoryTestSupport {
    @Autowired private VenueSeatLookupApi venueSeatLookup;

    @Test
    void 요청한_좌석의_주소만_반환한다() throws Exception {
        final Venue venue = persistVenue("올림픽홀", Region.SEOUL);
        final Seat a1 = persistSeat(venue, "A", "1", "1", 1);
        final Seat a2 = persistSeat(venue, "A", "1", "2", 1);
        persistSeat(venue, "B", "1", "1", 2);
        flushAndClear();

        final List<VenueSeatAddress> addresses =
                venueSeatLookup.findSeatAddresses(venue.getId(), Set.of(a1.getId(), a2.getId()));

        assertThat(addresses)
                .extracting(VenueSeatAddress::seatId)
                .containsExactlyInAnyOrder(a1.getId(), a2.getId());
        assertThat(addresses).extracting(VenueSeatAddress::section).containsOnly("A");
    }

    @Test
    void 다른_venue의_좌석은_id가_맞아도_반환하지_않는다() throws Exception {
        final Venue venue = persistVenue("올림픽홀", Region.SEOUL);
        final Venue other = persistVenue("벡스코", Region.GYEONGSANG);
        final Seat otherSeat = persistSeat(other, "A", "1", "1", 1);
        flushAndClear();

        assertThat(venueSeatLookup.findSeatAddresses(venue.getId(), Set.of(otherSeat.getId())))
                .isEmpty();
    }

    @Test
    void 빈_seatIds는_빈_목록이다() throws Exception {
        final Venue venue = persistVenue("올림픽홀", Region.SEOUL);
        flushAndClear();

        assertThat(venueSeatLookup.findSeatAddresses(venue.getId(), Set.of())).isEmpty();
    }

    @Test
    void venue의_모든_좌석_배치_좌표를_반환한다() throws Exception {
        final Venue venue = persistVenue("올림픽홀", Region.SEOUL);
        persistSeat(venue, "A", "1", "1", 1);
        persistSeat(venue, "A", "1", "2", 1);
        persistSeat(persistVenue("벡스코", Region.GYEONGSANG), "A", "1", "1", 1);
        flushAndClear();

        final List<VenueSeatLayout> layouts = venueSeatLookup.findAllSeatLayouts(venue.getId());

        assertThat(layouts).hasSize(2);
        assertThat(layouts).extracting(VenueSeatLayout::x).containsOnly(10.0);
        assertThat(layouts).extracting(VenueSeatLayout::y).containsOnly(20.0);
    }

    @Test
    void 좌석이_없는_venue는_빈_목록이다() throws Exception {
        final Venue venue = persistVenue("빈공연장", Region.SEOUL);
        flushAndClear();

        assertThat(venueSeatLookup.findAllSeatLayouts(venue.getId())).isEmpty();
    }
}
