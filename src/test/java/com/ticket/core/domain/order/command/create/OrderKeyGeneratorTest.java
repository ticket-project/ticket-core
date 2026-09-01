package com.ticket.core.domain.order.command.create;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("NonAsciiCharacters")
class OrderKeyGeneratorTest {

    private final OrderKeyGenerator orderKeyGenerator = new OrderKeyGenerator();

    @Test
    void 주문키는_ORDER_접두사와_하이픈없는_uuid로_생성한다() {
        //given
        //when
        String key = orderKeyGenerator.generate();

        //then
        assertThat(key).startsWith("ORDER-");
        assertThat(key.substring("ORDER-".length())).hasSize(32).doesNotContain("-");
    }

    @Test
    void 주문키를_두번_생성하면_서로_다르다() {
        //given
        //when
        String first = orderKeyGenerator.generate();
        String second = orderKeyGenerator.generate();

        //then
        assertThat(first).isNotEqualTo(second);
    }
}

