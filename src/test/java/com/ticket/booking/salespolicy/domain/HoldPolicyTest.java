package com.ticket.booking.salespolicy.domain;

import com.ticket.shared.exception.InvalidRequestException;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
}
