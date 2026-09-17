package com.ticket.show.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ticket.shared.exception.NotFoundException;
import com.ticket.show.query.PerformanceQueryPort;
import com.ticket.show.query.PerformanceSummaryView;
import com.ticket.venue.api.Region;
import com.ticket.venue.api.VenueLookupApi;
import com.ticket.venue.api.VenueSummary;

@SuppressWarnings("NonAsciiCharacters")
@ExtendWith(MockitoExtension.class)
class GetPerformanceSummaryUseCaseTest {
    @Mock private PerformanceQueryPort performanceQueryPort;
    @Mock private VenueLookupApi venueLookup;
    @InjectMocks private GetPerformanceSummaryUseCase useCase;

    @Test
    void 공연_요약정보를_반환한다() {
        LocalDateTime startTime = LocalDateTime.of(2026, 3, 20, 19, 30);
        when(performanceQueryPort.findByPerformanceId(1L))
                .thenReturn(Optional.of(new PerformanceSummaryView("싱어게인", 5L, startTime)));
        when(venueLookup.findSummary(5L))
                .thenReturn(
                        Optional.of(
                                new VenueSummary(
                                        5L,
                                        "venue",
                                        "주소",
                                        Region.CHUNGCHEONG,
                                        null,
                                        null,
                                        null,
                                        null,
                                        new VenueSummary.SeatMapLayout(0, 0, 0.0))));

        GetPerformanceSummaryUseCase.Output output =
                useCase.execute(new GetPerformanceSummaryUseCase.Input(1L));

        assertThat(output.title()).isEqualTo("싱어게인");
        assertThat(output.region()).isEqualTo("충청");
        assertThat(output.startTime()).isEqualTo(startTime);
    }

    @Test
    void 공연과_연결되지_않은_회차면_예외를_던진다() {
        when(performanceQueryPort.findByPerformanceId(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(new GetPerformanceSummaryUseCase.Input(1L)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void 공연장이_없어도_지역은_null로_반환한다() {
        LocalDateTime startTime = LocalDateTime.of(2026, 3, 20, 19, 30);
        when(performanceQueryPort.findByPerformanceId(1L))
                .thenReturn(Optional.of(new PerformanceSummaryView("싱어게인", null, startTime)));

        GetPerformanceSummaryUseCase.Output output =
                useCase.execute(new GetPerformanceSummaryUseCase.Input(1L));

        assertThat(output.region()).isNull();
    }
}
