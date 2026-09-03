package com.ticket.catalog.internal.application.performance.query;

import com.ticket.catalog.internal.application.performance.query.model.PerformanceSummaryView;
import com.ticket.catalog.internal.domain.show.Region;
import com.ticket.error.NotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@SuppressWarnings("NonAsciiCharacters")
@ExtendWith(MockitoExtension.class)
class GetPerformanceSummaryUseCaseTest {

    @Mock
    private PerformanceReadRepository performanceReadRepository;

    @InjectMocks
    private GetPerformanceSummaryUseCase useCase;

    @Test
    void 공연_요약정보를_반환한다() {
        LocalDateTime startTime = LocalDateTime.of(2026, 3, 20, 19, 30);
        when(performanceReadRepository.findByPerformanceId(1L))
                .thenReturn(Optional.of(new PerformanceSummaryView(
                        "싱어게인", Region.CHUNGCHEONG, startTime, 4
                )));

        GetPerformanceSummaryUseCase.Output output = useCase.execute(
                new GetPerformanceSummaryUseCase.Input(1L)
        );

        assertThat(output.title()).isEqualTo("싱어게인");
        assertThat(output.region()).isEqualTo("충청");
        assertThat(output.startTime()).isEqualTo(startTime);
        assertThat(output.maxCanHoldCount()).isEqualTo(4);
    }

    @Test
    void 공연과_연결되지_않은_회차면_예외를_던진다() {
        when(performanceReadRepository.findByPerformanceId(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(new GetPerformanceSummaryUseCase.Input(1L)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void 공연장이_없어도_지역은_null로_반환한다() {
        LocalDateTime startTime = LocalDateTime.of(2026, 3, 20, 19, 30);
        when(performanceReadRepository.findByPerformanceId(1L))
                .thenReturn(Optional.of(new PerformanceSummaryView(
                        "싱어게인", null, startTime, null
                )));

        GetPerformanceSummaryUseCase.Output output = useCase.execute(
                new GetPerformanceSummaryUseCase.Input(1L)
        );

        assertThat(output.region()).isNull();
        assertThat(output.maxCanHoldCount()).isNull();
    }
}
