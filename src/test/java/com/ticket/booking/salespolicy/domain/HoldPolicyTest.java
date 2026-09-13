package com.ticket.booking.salespolicy.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import com.ticket.shared.exception.InvalidRequestException;

@SuppressWarnings("NonAsciiCharacters")
class HoldPolicyTest {
    @Test
    void maxSeatCount가_null이면_무제한이다() {
        HoldPolicy policy = new HoldPolicy(null, Duration.ofSeconds(600));

        assertThat(policy.maxSeatCount()).isNull();
        assertThat(policy.exceeds(1_000)).isFalse();
    }

    @Test
    void maxSeatCount가_2이면_경계값을_허용한다() {
        HoldPolicy policy = new HoldPolicy(2, Duration.ofSeconds(600));

        assertThat(policy.exceeds(2)).isFalse();
        assertThat(policy.exceeds(3)).isTrue();
    }

    @Test
    void maxSeatCount가_2_미만이면_거절한다() {
        assertThatThrownBy(() -> new HoldPolicy(1, Duration.ofSeconds(600)))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void holdDuration은_양수여야_한다() {
        HoldPolicy policy = new HoldPolicy(4, Duration.ofSeconds(1));
        assertThat(policy.holdDuration()).isEqualTo(Duration.ofSeconds(1));

        assertThatThrownBy(() -> new HoldPolicy(4, Duration.ZERO))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> new HoldPolicy(4, Duration.ofSeconds(-1)))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> new HoldPolicy(4, null))
                .isInstanceOf(InvalidRequestException.class);
    }

    /**
     * 재현한 문제: Duration이 양수여도 초 단위로 저장할 때 잘려 0이 될 수 있다. {@code Duration.ofMillis(500)}은 {@code
     * isZero}/{@code isNegative}를 통과하지만 {@code getSeconds()}가 0이라 hold TTL이 0으로 저장된다.
     */
    @Test
    void 일초_미만이나_소수_초는_조용히_잘리지_않고_거부된다() {
        // 공개 메시지는 InvalidRequestException의 고정 문구라 여기서 문구를 고정하지 않는다.
        assertThatThrownBy(() -> new HoldPolicy(4, Duration.ofMillis(500)))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> new HoldPolicy(4, Duration.ofMillis(1500)))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> new HoldPolicy(4, Duration.ofNanos(1)))
                .isInstanceOf(InvalidRequestException.class);
    }

    /** 기존 설정·시드가 쓰는 정수 초 입력은 그대로 통과한다. */
    @Test
    void 정수_초_입력은_그대로_보존된다() {
        assertThat(new HoldPolicy(4, Duration.ofSeconds(600)).holdDuration())
                .isEqualTo(Duration.ofSeconds(600));
        assertThat(new HoldPolicy(4, Duration.ofMinutes(10)).holdDuration())
                .isEqualTo(Duration.ofSeconds(600));
    }
}
