package com.ticket.core.domain.performance.policy;

import com.ticket.support.error.CoreException;
import com.ticket.core.domain.error.DomainErrorType;
import com.ticket.core.domain.performance.query.model.PerformanceBookingPolicySnapshot;
import com.ticket.core.domain.queue.model.QueueMode;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SuppressWarnings("NonAsciiCharacters")
class BookingPolicyValidatorTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 4, 10, 0);

    @Test
    void 예매가능시간_안이면_통과한다() {
        PerformanceBookingPolicySnapshot policy = policy(NOW.minusMinutes(1), NOW.plusMinutes(1), 4);

        assertThatCode(() -> BookingPolicyValidator.ensureBookingOpen(policy, NOW)).doesNotThrowAnyException();
    }

    @Test
    void 예매시작시각과_같으면_통과한다() {
        PerformanceBookingPolicySnapshot policy = policy(NOW, NOW.plusHours(1), 4);

        assertThatCode(() -> BookingPolicyValidator.ensureBookingOpen(policy, NOW)).doesNotThrowAnyException();
    }

    @Test
    void 예매마감시각과_같으면_통과한다() {
        PerformanceBookingPolicySnapshot policy = policy(NOW.minusHours(1), NOW, 4);

        assertThatCode(() -> BookingPolicyValidator.ensureBookingOpen(policy, NOW)).doesNotThrowAnyException();
    }

    @Test
    void 예매시작_전이면_실패한다() {
        assertBookingError(policy(NOW.plusMinutes(1), NOW.plusHours(1), 4), DomainErrorType.NOT_YET_RESERVE_TIME);
    }

    @Test
    void 예매마감_후면_실패한다() {
        assertBookingError(policy(NOW.minusHours(1), NOW.minusMinutes(1), 4), DomainErrorType.PERFORMANCE_IS_PAST);
    }

    @Test
    void 예매시작시각이_없으면_실패한다() {
        assertBookingError(policy(null, NOW.plusHours(1), 4), DomainErrorType.NOT_YET_RESERVE_TIME);
    }

    @Test
    void 예매마감시각이_없으면_실패한다() {
        assertBookingError(policy(NOW.minusHours(1), null, 4), DomainErrorType.PERFORMANCE_IS_PAST);
    }

    @Test
    void 판정은_전달받은_시각을_기준으로_한다() {
        PerformanceBookingPolicySnapshot policy = policy(NOW.minusMinutes(1), NOW.plusMinutes(1), 4);

        assertThatCode(() -> BookingPolicyValidator.ensureBookingOpen(policy, NOW)).doesNotThrowAnyException();
        assertThatThrownBy(() -> BookingPolicyValidator.ensureBookingOpen(policy, NOW.plusHours(1)))
                .isInstanceOf(CoreException.class);
    }

    @Test
    void 요청좌석수가_한도보다_적으면_통과한다() {
        PerformanceBookingPolicySnapshot policy = openPolicy(3);

        assertThatCode(() -> BookingPolicyValidator.ensureWithinHoldLimit(policy, 2)).doesNotThrowAnyException();
    }

    @Test
    void 요청좌석수가_한도와_같으면_통과한다() {
        PerformanceBookingPolicySnapshot policy = openPolicy(3);

        assertThatCode(() -> BookingPolicyValidator.ensureWithinHoldLimit(policy, 3)).doesNotThrowAnyException();
    }

    @Test
    void 요청좌석수가_한도를_넘으면_실패한다() {
        PerformanceBookingPolicySnapshot policy = openPolicy(3);

        assertThatThrownBy(() -> BookingPolicyValidator.ensureWithinHoldLimit(policy, 4))
                .isInstanceOf(CoreException.class)
                .satisfies(exception -> assertThat(((CoreException) exception).getErrorType())
                        .isEqualTo(DomainErrorType.EXCEED_HOLD_LIMIT));
    }

    @Test
    void 한도가_없으면_좌석_수를_제한하지_않는다() {
        PerformanceBookingPolicySnapshot policy = openPolicy(null);

        assertThatCode(() -> BookingPolicyValidator.ensureWithinHoldLimit(policy, 999)).doesNotThrowAnyException();
    }

    @Test
    void 대기열_필요_여부는_정책의_대기열_설정을_따른다() {
        assertThat(BookingPolicyValidator.requiresQueue(queuePolicy(QueueMode.FORCE_ON), NOW)).isTrue();
        assertThat(BookingPolicyValidator.requiresQueue(queuePolicy(QueueMode.FORCE_OFF), NOW)).isFalse();
        assertThat(BookingPolicyValidator.requiresQueue(queuePolicy(null), NOW)).isFalse();
    }

    private void assertBookingError(final PerformanceBookingPolicySnapshot policy, final DomainErrorType errorType) {
        assertThatThrownBy(() -> BookingPolicyValidator.ensureBookingOpen(policy, NOW))
                .isInstanceOf(CoreException.class)
                .satisfies(exception -> assertThat(((CoreException) exception).getErrorType()).isEqualTo(errorType));
    }

    private PerformanceBookingPolicySnapshot openPolicy(final Integer maxCanHoldCount) {
        return policy(NOW.minusHours(1), NOW.plusHours(1), maxCanHoldCount);
    }

    private PerformanceBookingPolicySnapshot queuePolicy(final QueueMode queueMode) {
        return new PerformanceBookingPolicySnapshot(
                10L, NOW.minusHours(1), NOW.plusHours(1), 4, 300, queueMode, null, null, null, null);
    }

    private PerformanceBookingPolicySnapshot policy(
            final LocalDateTime orderOpenTime,
            final LocalDateTime orderCloseTime,
            final Integer maxCanHoldCount
    ) {
        return new PerformanceBookingPolicySnapshot(
                10L, orderOpenTime, orderCloseTime, maxCanHoldCount, 300, null, null, null, null, null);
    }
}
