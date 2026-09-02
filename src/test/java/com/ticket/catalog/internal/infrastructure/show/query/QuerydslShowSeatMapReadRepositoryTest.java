package com.ticket.catalog.internal.infrastructure.show.query;

import com.ticket.catalog.ShowSeatMapEntry;
import com.ticket.catalog.internal.application.show.query.ShowSeatMapReadRepository;
import com.ticket.catalog.internal.domain.seat.Seat;
import com.ticket.catalog.internal.domain.show.Region;
import com.ticket.catalog.internal.domain.show.Show;
import com.ticket.catalog.internal.domain.show.ShowGrade;
import com.ticket.catalog.internal.domain.show.Venue;
import com.ticket.core.infra.support.InfraReadRepositoryTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Import(QuerydslShowSeatMapReadRepository.class)
@SuppressWarnings("NonAsciiCharacters")
class QuerydslShowSeatMapReadRepositoryTest extends InfraReadRepositoryTestSupport {

    @Autowired
    private ShowSeatMapReadRepository showSeatMapReadRepository;

    private Long showId;

    @BeforeEach
    void setUp() throws Exception {
        Venue venue = persistVenue("venue", Region.SEOUL);
        Show show = persistShow("show", venue, null, 10L, LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(5));
        showId = show.getId();
        ShowGrade vip = persistShowGrade(show, "VIP", "VIP", BigDecimal.valueOf(150000), 1);
        ShowGrade r = persistShowGrade(show, "R", "R", BigDecimal.valueOf(100000), 2);
        Seat seat1 = persistSeat("A", "01", "02", 1);
        Seat seat2 = persistSeat("A", "01", "01", 1);
        persistShowSeat(show, seat1, r);
        persistShowSeat(show, seat2, vip);
        flushAndClear();
    }

    @Test
    void 공연_좌석_정보를_정렬해서_조회한다() {
        List<ShowSeatMapEntry> result = showSeatMapReadRepository.findSeatMap(showId);

        assertThat(result).extracting(ShowSeatMapEntry::seatId).hasSize(2);
        assertThat(result).extracting(ShowSeatMapEntry::gradeCode).containsExactly("VIP", "R");
        assertThat(result).extracting(ShowSeatMapEntry::seatNo).containsExactly("01", "02");
    }
}
