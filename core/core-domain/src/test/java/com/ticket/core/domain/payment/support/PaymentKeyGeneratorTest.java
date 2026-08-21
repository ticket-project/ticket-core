package com.ticket.core.domain.payment.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("NonAsciiCharacters")
class PaymentKeyGeneratorTest {

    @Test
    void 발급한_키는_접두어를_가지고_매번_다르다() {
        final PaymentKeyGenerator generator = new PaymentKeyGenerator();

        final String first = generator.generate();
        final String second = generator.generate();

        assertThat(first).startsWith("PAY-");
        assertThat(first).hasSize(36);
        assertThat(first).isNotEqualTo(second);
    }
}
