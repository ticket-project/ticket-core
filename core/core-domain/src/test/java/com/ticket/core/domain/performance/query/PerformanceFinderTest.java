package com.ticket.core.domain.performance.query;

import com.ticket.core.domain.performance.model.Performance;
import com.ticket.core.domain.performance.repository.PerformanceRepository;
import com.ticket.core.support.exception.CoreException;
import com.ticket.core.support.exception.ErrorType;
import com.ticket.core.support.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@SuppressWarnings("NonAsciiCharacters")
@ExtendWith(MockitoExtension.class)
class PerformanceFinderTest {

    private static final LocalDateTime FIXED_NOW = LocalDateTime.of(2026, 3, 15, 19, 0);

    @Mock
    private PerformanceRepository performanceRepository;

    private PerformanceFinder performanceFinder;

    @BeforeEach
    void setUp() {
        this.performanceFinder = new PerformanceFinder(performanceRepository);
    }

    @Test
    void 예약중인_공연이면_그대로_반환한다() {
        //given
        Performance performance = createPerformance(
                LocalDateTime.of(2026, 3, 15, 18, 50),
                LocalDateTime.of(2026, 3, 15, 19, 10)
        );
        when(performanceRepository.findWithQueuePolicyById(1L)).thenReturn(Optional.of(performance));

        //when
        Performance result = performanceFinder.findOpenPerformance(1L, FIXED_NOW);

        //then
        assertThat(result).isSameAs(performance);
    }

    @Test
    void 예약중이_아니면_찾을수없음_예외를_던진다() {
        //given
        Performance performance = createPerformance(
                LocalDateTime.of(2026, 3, 15, 19, 10),
                LocalDateTime.of(2026, 3, 15, 19, 20)
        );
        when(performanceRepository.findWithQueuePolicyById(1L)).thenReturn(Optional.of(performance));

        //when
        //then
        assertThatThrownBy(() -> performanceFinder.findOpenPerformance(1L, FIXED_NOW))
                .isInstanceOf(NotFoundException.class)
                .satisfies(thrown -> assertThat(((NotFoundException) thrown).getErrorType()).isEqualTo(ErrorType.NOT_FOUND_DATA));
    }

    @Test
    void 공연이_없으면_findById가_찾을수없음_예외를_던진다() {
        //given
        when(performanceRepository.findWithQueuePolicyById(1L)).thenReturn(Optional.empty());

        //when
        //then
        assertThatThrownBy(() -> performanceFinder.findById(1L))
                .isInstanceOf(CoreException.class)
                .satisfies(thrown -> assertThat(((CoreException) thrown).getErrorType()).isEqualTo(ErrorType.NOT_FOUND_DATA));
    }

    @Test
    void 예매중이지만_아직_예매시간_전이면_예외를_던진다() {
        //given
        Performance performance = createPerformance(
                LocalDateTime.of(2026, 3, 15, 19, 10),
                LocalDateTime.of(2026, 3, 15, 19, 20)
        );
        when(performanceRepository.findWithQueuePolicyById(1L)).thenReturn(Optional.of(performance));

        //when
        //then
        assertThatThrownBy(() -> performanceFinder.findValidPerformanceById(1L, FIXED_NOW))
                .isInstanceOf(CoreException.class)
                .satisfies(thrown -> assertThat(((CoreException) thrown).getErrorType()).isEqualTo(ErrorType.NOT_YET_RESERVE_TIME));
    }

    @Test
    void 예매시작시간이_null이면_아직_예매시간_전_예외를_던진다() {
        //given
        Performance performance = createPerformance(
                null,
                LocalDateTime.of(2026, 3, 15, 19, 20)
        );
        when(performanceRepository.findWithQueuePolicyById(1L)).thenReturn(Optional.of(performance));

        //when
        //then
        assertThatThrownBy(() -> performanceFinder.findValidPerformanceById(1L, FIXED_NOW))
                .isInstanceOf(CoreException.class)
                .satisfies(thrown -> assertThat(((CoreException) thrown).getErrorType()).isEqualTo(ErrorType.NOT_YET_RESERVE_TIME));
    }

    @Test
    void 예매마감된_공연은_종료_예외를_던진다() {
        //given
        Performance performance = createPerformance(
                LocalDateTime.of(2026, 3, 15, 18, 40),
                LocalDateTime.of(2026, 3, 15, 18, 50)
        );
        when(performanceRepository.findWithQueuePolicyById(1L)).thenReturn(Optional.of(performance));

        //when
        //then
        assertThatThrownBy(() -> performanceFinder.findValidPerformanceById(1L, FIXED_NOW))
                .isInstanceOf(CoreException.class)
                .satisfies(thrown -> assertThat(((CoreException) thrown).getErrorType()).isEqualTo(ErrorType.PERFORMANCE_IS_PAST));
    }

    @Test
    void 예매마감시간이_null이면_종료_예외를_던진다() {
        //given
        Performance performance = createPerformance(
                LocalDateTime.of(2026, 3, 15, 18, 40),
                null
        );
        when(performanceRepository.findWithQueuePolicyById(1L)).thenReturn(Optional.of(performance));

        //when
        //then
        assertThatThrownBy(() -> performanceFinder.findValidPerformanceById(1L, FIXED_NOW))
                .isInstanceOf(CoreException.class)
                .satisfies(thrown -> assertThat(((CoreException) thrown).getErrorType()).isEqualTo(ErrorType.PERFORMANCE_IS_PAST));
    }

    @Test
    void 예매가능한_공연이면_findValidPerformanceById가_반환한다() {
        //given
        Performance performance = createPerformance(
                LocalDateTime.of(2026, 3, 15, 18, 50),
                LocalDateTime.of(2026, 3, 15, 19, 10)
        );
        when(performanceRepository.findWithQueuePolicyById(1L)).thenReturn(Optional.of(performance));

        //when
        Performance result = performanceFinder.findValidPerformanceById(1L, FIXED_NOW);

        //then
        assertThat(result).isSameAs(performance);
    }

    private Performance createPerformance(final LocalDateTime orderOpenTime, final LocalDateTime orderCloseTime) {
        return new Performance(
                null,
                1L,
                LocalDateTime.of(2026, 3, 16, 19, 0),
                LocalDateTime.of(2026, 3, 16, 21, 0),
                orderOpenTime,
                orderCloseTime,
                4,
                300
        );
    }
}

