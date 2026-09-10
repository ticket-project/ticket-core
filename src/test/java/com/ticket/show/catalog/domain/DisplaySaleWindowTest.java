package com.ticket.show.catalog.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link DisplaySaleWindow#statusAt}이 {@link SaleDisplayStatus} 판정의 유일한 원본이다(TD-12
 * 해소). {@code SaleDisplayStatusPredicateFactory}가 같은 결론을 내는지는
 * {@code SaleDisplayStatusPredicateFactoryTest}가 같은 케이스로 고정한다.
 */
@SuppressWarnings("NonAsciiCharacters")
class DisplaySaleWindowTest {

    @Test
    void 시작과_종료가_모두_없으면_CLOSED다() {
        DisplaySaleWindow window = new DisplaySaleWindow(null, null);

        assertThat(window.statusAt(LocalDateTime.now())).isEqualTo(SaleDisplayStatus.CLOSED);
    }

    @Test
    void 시작만_없으면_CLOSED다() {
        DisplaySaleWindow window = new DisplaySaleWindow(null, LocalDateTime.now().plusHours(1));

        assertThat(window.statusAt(LocalDateTime.now())).isEqualTo(SaleDisplayStatus.CLOSED);
    }

    @Test
    void 종료만_없으면_CLOSED다() {
        DisplaySaleWindow window = new DisplaySaleWindow(LocalDateTime.now().minusHours(1), null);

        assertThat(window.statusAt(LocalDateTime.now())).isEqualTo(SaleDisplayStatus.CLOSED);
    }

    @Test
    void 판매시작전이면_BEFORE_OPEN이다() {
        DisplaySaleWindow window = new DisplaySaleWindow(
                LocalDateTime.now().plusMinutes(10), LocalDateTime.now().plusHours(1));

        assertThat(window.statusAt(LocalDateTime.now())).isEqualTo(SaleDisplayStatus.BEFORE_OPEN);
    }

    @Test
    void 판매기간중이면_ON_SALE이다() {
        DisplaySaleWindow window = new DisplaySaleWindow(
                LocalDateTime.now().minusMinutes(10), LocalDateTime.now().plusMinutes(10));

        assertThat(window.statusAt(LocalDateTime.now())).isEqualTo(SaleDisplayStatus.ON_SALE);
    }

    @Test
    void 판매시작시각과_같으면_ON_SALE이다() {
        LocalDateTime now = LocalDateTime.now();
        DisplaySaleWindow window = new DisplaySaleWindow(now, now.plusMinutes(10));

        assertThat(window.statusAt(now)).isEqualTo(SaleDisplayStatus.ON_SALE);
    }

    @Test
    void 판매종료시각과_같으면_ON_SALE이다() {
        LocalDateTime now = LocalDateTime.now();
        DisplaySaleWindow window = new DisplaySaleWindow(now.minusMinutes(10), now);

        assertThat(window.statusAt(now)).isEqualTo(SaleDisplayStatus.ON_SALE);
    }

    @Test
    void 판매종료후면_CLOSED다() {
        DisplaySaleWindow window = new DisplaySaleWindow(
                LocalDateTime.now().minusHours(1), LocalDateTime.now().minusMinutes(10));

        assertThat(window.statusAt(LocalDateTime.now())).isEqualTo(SaleDisplayStatus.CLOSED);
    }
}
