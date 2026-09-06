package com.ticket.catalog.application.publicapi;

import com.ticket.catalog.PerformanceVenueLayout;
import com.ticket.catalog.application.performance.query.PerformanceVenueLayoutReadRepository;
import com.ticket.catalog.application.performance.query.PerformanceVenueLayoutReadRepository.PerformanceGradeLayoutRow;
import com.ticket.catalog.application.performance.query.PerformanceVenueLayoutReadRepository.SeatLayoutRow;
import com.ticket.catalog.domain.performance.query.PerformanceVenueLayoutContext;
import com.ticket.error.NotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@SuppressWarnings("NonAsciiCharacters")
@ExtendWith(MockitoExtension.class)
class PerformanceVenueLayoutCatalogServiceTest {

    @Mock
    private PerformanceVenueLayoutReadRepository performanceVenueLayoutReadRepository;

    @InjectMocks
    private PerformanceVenueLayoutCatalogService service;

    @Test
    void 존재하지_않는_회차면_NotFoundException을_던진다() {
        when(performanceVenueLayoutReadRepository.findVenueLayoutContext(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getVenueLayout(1L))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void venue가_없는_show면_seatLayout이_빈_맵이다() {
        when(performanceVenueLayoutReadRepository.findVenueLayoutContext(1L)).thenReturn(Optional.of(
                new PerformanceVenueLayoutContext(1L, null, null, null, null, null)));
        when(performanceVenueLayoutReadRepository.findGradeLayouts(1L)).thenReturn(List.of());

        final PerformanceVenueLayout layout = service.getVenueLayout(1L);

        assertThat(layout.seatLayoutBySeatId()).isEmpty();
    }

    @Test
    void venue의_좌석_좌표와_회차_grade를_조합한다() {
        when(performanceVenueLayoutReadRepository.findVenueLayoutContext(1L)).thenReturn(Optional.of(
                new PerformanceVenueLayoutContext(1L, 3L, "venue", 500, 356, 4.8)));
        when(performanceVenueLayoutReadRepository.findAllSeatLayouts(3L))
                .thenReturn(List.of(new SeatLayoutRow(10L, 1, "가", "A", "1", 129.0, 101.0)));
        when(performanceVenueLayoutReadRepository.findGradeLayouts(1L))
                .thenReturn(List.of(new PerformanceGradeLayoutRow(100L, "VIP", "VIP석", 1)));

        final PerformanceVenueLayout layout = service.getVenueLayout(1L);

        assertThat(layout.venueId()).isEqualTo(3L);
        assertThat(layout.viewBoxWidth()).isEqualTo(500);
        assertThat(layout.seatLayoutBySeatId()).containsKey(10L);
        assertThat(layout.seatLayoutBySeatId().get(10L).x()).isEqualTo(129.0);
        assertThat(layout.gradeLayoutByPerformanceGradeId().get(100L).gradeCode()).isEqualTo("VIP");
    }
}
