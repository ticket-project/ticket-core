package com.ticket.show.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ticket.shared.exception.NotFoundException;
import com.ticket.show.api.PerformanceSaleSnapshot;
import com.ticket.show.domain.performance.PerformanceRepository;
import com.ticket.show.domain.performance.PerformanceSaleContext;
import com.ticket.venue.api.VenueLookupApi;
import com.ticket.venue.api.VenueSeatAddress;
import com.ticket.venue.api.VenueSeatLookupApi;

@SuppressWarnings("NonAsciiCharacters")
@ExtendWith(MockitoExtension.class)
class PerformanceSaleCatalogServiceTest {
    @Mock private PerformanceRepository performanceRepository;
    @Mock private VenueLookupApi venueLookup;
    @Mock private VenueSeatLookupApi venueSeatLookup;
    @InjectMocks private PerformanceSaleCatalogService service;

    @Test
    void 존재하지_않는_회차면_NotFoundException을_던진다() {
        when(performanceRepository.findSaleContext(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getSaleSnapshot(1L, Set.of(10L)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void venue가_없는_show면_seatInfo가_빈_맵이다() {
        when(performanceRepository.findSaleContext(1L))
                .thenReturn(Optional.of(new PerformanceSaleContext(1L, 2L, "show", null, null)));
        when(performanceRepository.findPerformanceGrades(1L)).thenReturn(List.of());

        final PerformanceSaleSnapshot snapshot = service.getSaleSnapshot(1L, Set.of(10L));

        assertThat(snapshot.seatInfoBySeatId()).isEmpty();
    }

    @Test
    void venue에_속한_좌석과_회차_grade를_snapshot으로_조합한다() {
        when(performanceRepository.findSaleContext(1L))
                .thenReturn(Optional.of(new PerformanceSaleContext(1L, 2L, "show", 3L, null)));
        when(venueSeatLookup.findSeatAddresses(3L, Set.of(10L)))
                .thenReturn(List.of(new VenueSeatAddress(10L, 1, "가", "A", "1")));
        when(performanceRepository.findPerformanceGrades(1L))
                .thenReturn(
                        List.of(
                                new PerformanceSaleSnapshot.GradeInfo(
                                        100L, "VIP", "VIP석", 1, new BigDecimal("170000"))));

        final PerformanceSaleSnapshot snapshot = service.getSaleSnapshot(1L, Set.of(10L));

        assertThat(snapshot.seatInfoBySeatId()).containsKey(10L);
        assertThat(snapshot.seatInfoBySeatId().get(10L).label()).isEqualTo("1F 가구역 A열 1번");
        assertThat(snapshot.gradeInfoByPerformanceGradeId().get(100L).gradeCode()).isEqualTo("VIP");
    }
}
