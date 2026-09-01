package com.ticket.core.domain.hold.command;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("NonAsciiCharacters")
class HoldKeyGeneratorTest {

    private final HoldKeyGenerator holdKeyGenerator = new HoldKeyGenerator();

    @Test
    void hold키는_HOLD_접두사와_하이픈없는_uuid로_생성한다() {
        //given
        //when
        String key = holdKeyGenerator.generate();

        //then
        assertThat(key).startsWith("HOLD-");
        assertThat(key.substring("HOLD-".length())).hasSize(32).doesNotContain("-");
    }

    @Test
    void hold키를_두번_생성하면_서로_다르다() {
        //given
        //when
        String first = holdKeyGenerator.generate();
        String second = holdKeyGenerator.generate();

        //then
        assertThat(first).isNotEqualTo(second);
    }
}

