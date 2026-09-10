package com.ticket.show.catalog.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 판매 표시 상태 판정 자체의 경계값·null 케이스는 {@link DisplaySaleWindowTest}가 고정한다.
 * 여기서는 {@link Show#saleDisplayStatusAt}이 {@code DisplaySaleWindow}에 위임하는지와
 * 생성자가 표시 필드를 올바르게 채우는지만 확인한다.
 */
@SuppressWarnings("NonAsciiCharacters")
class ShowTest {

    @Test
    void 생성자는_displaySaleWindow를_채운다() {
        LocalDateTime startsAt = LocalDateTime.now().minusMinutes(10);
        LocalDateTime endsAt = LocalDateTime.now().plusMinutes(10);

        Show show = createShow(startsAt, endsAt);

        assertThat(show.getDisplaySaleStartsAt()).isEqualTo(startsAt);
        assertThat(show.getDisplaySaleEndsAt()).isEqualTo(endsAt);
        assertThat(show.getDisplaySaleType()).isEqualTo(SaleType.GENERAL);
    }

    @Test
    void saleDisplayStatusAt은_displaySaleWindow의_판정에_위임한다() {
        LocalDateTime now = LocalDateTime.now();
        Show show = createShow(now.minusMinutes(10), now.plusMinutes(10));

        assertThat(show.saleDisplayStatusAt(now)).isEqualTo(SaleDisplayStatus.ON_SALE);
    }

    private Show createShow(final LocalDateTime displaySaleStartsAt, final LocalDateTime displaySaleEndsAt) {
        return new Show(
                "공연",
                "부제",
                "소개",
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 3, 31),
                0L,
                SaleType.GENERAL,
                displaySaleStartsAt,
                displaySaleEndsAt,
                "image",
                null,
                null,
                120
        );
    }
}
