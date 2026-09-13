package com.ticket.show.classification.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

@SuppressWarnings("NonAsciiCharacters")
class CategoryTest {
    @Test
    void 카테고리를_정적팩토리로_생성한다() {
        Category category = Category.of("CONCERT", "콘서트");

        assertThat(category.getCode()).isEqualTo("CONCERT");
        assertThat(category.getName()).isEqualTo("콘서트");
    }
}
