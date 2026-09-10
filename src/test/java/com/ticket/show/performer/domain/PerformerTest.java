package com.ticket.show.performer.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("NonAsciiCharacters")
class PerformerTest {

    @Test
    void 출연자를_정적팩토리로_생성한다() {
        Performer performer = Performer.create("아이유", "https://example.com/performer.png");

        assertThat(performer.getName()).isEqualTo("아이유");
        assertThat(performer.getProfileImageUrl()).isEqualTo("https://example.com/performer.png");
    }
}
