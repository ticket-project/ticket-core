package com.ticket.show.infrastructure;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.ticket.show.domain.SaleDisplayStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 경계값·null 케이스가 {@link com.ticket.show.domain.DisplaySaleWindowTest}와 같은 결론을
 * 내는지는 실제 DB 조회 통합 테스트(QuerydslShowListQueryPortTest 등)가 고정한다. 여기서는
 * 각 상태가 만들어내는 조건식의 형태만 고정한다.
 */
@SuppressWarnings("NonAsciiCharacters")
class SaleDisplayStatusPredicateFactoryTest {

    private final SaleDisplayStatusPredicateFactory saleDisplayStatusPredicateFactory = new SaleDisplayStatusPredicateFactory();
    private final LocalDateTime fixedNow = LocalDateTime.of(2026, 3, 15, 19, 0);

    @Test
    void saleDisplayStatus가_null이면_null을_반환한다() {
        assertThat(saleDisplayStatusPredicateFactory.condition(null, fixedNow)).isNull();
    }

    @Test
    void saleDisplayStatus별로_고정시각_기준_조건식을_반환한다() {
        BooleanExpression beforeOpen = saleDisplayStatusPredicateFactory.condition(SaleDisplayStatus.BEFORE_OPEN, fixedNow);
        BooleanExpression onSale = saleDisplayStatusPredicateFactory.condition(SaleDisplayStatus.ON_SALE, fixedNow);
        BooleanExpression closed = saleDisplayStatusPredicateFactory.condition(SaleDisplayStatus.CLOSED, fixedNow);

        assertThat(beforeOpen).isNotNull();
        assertThat(onSale).isNotNull();
        assertThat(closed).isNotNull();
        assertThat(beforeOpen.toString()).contains("2026-03-15T19:00");
        assertThat(onSale.toString()).contains("2026-03-15T19:00");
        assertThat(closed.toString()).contains("2026-03-15T19:00");
    }

    @Test
    void CLOSED_조건식은_null_창도_포함한다() {
        BooleanExpression closed = saleDisplayStatusPredicateFactory.condition(SaleDisplayStatus.CLOSED, fixedNow);

        assertThat(closed.toString()).contains("is null");
    }
}
