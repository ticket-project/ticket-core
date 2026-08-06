package com.ticket.core.domain.performance.query;

import com.ticket.core.domain.performance.query.model.PerformanceBookingPolicyView;
import com.ticket.core.support.exception.CoreException;
import com.ticket.core.support.exception.ErrorType;
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

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class PerformanceBookingPolicyFinderTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 4, 10, 0);

    @Mock
    private PerformanceBookingPolicyQueryRepository queryRepository;

    @InjectMocks
    private PerformanceBookingPolicyFinder finder;

    @Test
    void 예매가능시간이면_불변_정책을_반환한다() {
        PerformanceBookingPolicyView policy = policy(NOW.minusMinutes(1), NOW.plusMinutes(1));
        when(queryRepository.findByPerformanceId(10L)).thenReturn(Optional.of(policy));

        assertThat(finder.findValidById(10L, NOW)).isSameAs(policy);
    }

    @Test
    void 예매시작_전이면_실패한다() {
        when(queryRepository.findByPerformanceId(10L))
                .thenReturn(Optional.of(policy(NOW.plusMinutes(1), NOW.plusHours(1))));

        assertError(ErrorType.NOT_YET_RESERVE_TIME);
    }

    @Test
    void 예매마감_후면_실패한다() {
        when(queryRepository.findByPerformanceId(10L))
                .thenReturn(Optional.of(policy(NOW.minusHours(1), NOW.minusMinutes(1))));

        assertError(ErrorType.PERFORMANCE_IS_PAST);
    }

    private void assertError(final ErrorType errorType) {
        assertThatThrownBy(() -> finder.findValidById(10L, NOW))
                .isInstanceOf(CoreException.class)
                .satisfies(exception -> assertThat(((CoreException) exception).getErrorType()).isEqualTo(errorType));
    }

    private PerformanceBookingPolicyView policy(
            final LocalDateTime orderOpenTime,
            final LocalDateTime orderCloseTime
    ) {
        return new PerformanceBookingPolicyView(
                10L,
                orderOpenTime,
                orderCloseTime,
                4,
                300,
                null,
                null,
                null,
                null,
                null
        );
    }
}
