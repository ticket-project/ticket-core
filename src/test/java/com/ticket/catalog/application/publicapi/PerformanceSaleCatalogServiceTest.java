package com.ticket.catalog.application.publicapi;

import com.ticket.catalog.PerformanceSaleSnapshot;
import com.ticket.catalog.application.performance.query.PerformanceSaleReadRepository;
import com.ticket.catalog.application.performance.query.PerformanceSaleReadRepository.PerformanceGradeRow;
import com.ticket.catalog.application.performance.query.PerformanceSaleReadRepository.SeatAddressRow;
import com.ticket.catalog.domain.performance.query.PerformanceSaleContext;
import com.ticket.error.NotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@SuppressWarnings("NonAsciiCharacters")
@ExtendWith(MockitoExtension.class)
class PerformanceSaleCatalogServiceTest {

    @Mock
    private PerformanceSaleReadRepository performanceSaleReadRepository;

    @InjectMocks
    private PerformanceSaleCatalogService service;

    @Test
    void 존재하지_않는_회차면_NotFoundException을_던진다() {
        when(performanceSaleReadRepository.findContext(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getSaleSnapshot(1L, Set.of(10L)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void venue가_없는_show면_seatInfo가_빈_맵이다() {
        when(performanceSaleReadRepository.findContext(1L)).thenReturn(Optional.of(
                new PerformanceSaleContext(1L, 2L, "show", null, null, null)));
        when(performanceSaleReadRepository.findPerformanceGrades(1L)).thenReturn(List.of());

        final PerformanceSaleSnapshot snapshot = service.getSaleSnapshot(1L, Set.of(10L));

        assertThat(snapshot.seatInfoBySeatId()).isEmpty();
    }

    @Test
    void venue에_속한_좌석과_회차_grade를_snapshot으로_조합한다() {
        when(performanceSaleReadRepository.findContext(1L)).thenReturn(Optional.of(
                new PerformanceSaleContext(1L, 2L, "show", 3L, "venue", null)));
        when(performanceSaleReadRepository.findSeatAddresses(3L, Set.of(10L)))
                .thenReturn(List.of(new SeatAddressRow(10L, 1, "가", "A", "1")));
        when(performanceSaleReadRepository.findPerformanceGrades(1L))
                .thenReturn(List.of(new PerformanceGradeRow(100L, "VIP", "VIP석", 1, new BigDecimal("170000"))));

        final PerformanceSaleSnapshot snapshot = service.getSaleSnapshot(1L, Set.of(10L));

        assertThat(snapshot.seatInfoBySeatId()).containsKey(10L);
        assertThat(snapshot.seatInfoBySeatId().get(10L).label()).isEqualTo("1F 가구역 A열 1번");
        assertThat(snapshot.gradeInfoByPerformanceGradeId().get(100L).gradeCode()).isEqualTo("VIP");
    }
}
