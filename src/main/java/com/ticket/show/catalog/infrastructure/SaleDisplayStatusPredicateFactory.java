package com.ticket.show.catalog.infrastructure;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.ticket.show.catalog.domain.SaleDisplayStatus;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

import static com.ticket.show.catalog.domain.QShow.show;

/**
 * {@link SaleDisplayStatus} 판정의 유일한 원본은 {@code DisplaySaleWindow.statusAt(now)}다.
 * 여기서는 그 판정과 같은 결론을 내는 Querydsl 조건만 만든다 — null 창(시작·종료 중 하나라도
 * 없음)은 CLOSED로 본다(TD-12: 예전에는 이 null 처리가 도메인 판정과 달라서 필터 결과가
 * 어긋났다).
 */
@Component
public class SaleDisplayStatusPredicateFactory {

    public BooleanExpression condition(final SaleDisplayStatus saleDisplayStatus, final LocalDateTime now) {
        if (saleDisplayStatus == null) {
            return null;
        }

        final var startsAt = show.displaySaleWindow.startsAt;
        final var endsAt = show.displaySaleWindow.endsAt;

        return switch (saleDisplayStatus) {
            case BEFORE_OPEN -> startsAt.isNotNull().and(endsAt.isNotNull()).and(startsAt.gt(now));
            case ON_SALE -> startsAt.isNotNull().and(endsAt.isNotNull())
                    .and(startsAt.loe(now)).and(endsAt.goe(now));
            case CLOSED -> startsAt.isNull().or(endsAt.isNull()).or(endsAt.lt(now));
        };
    }
}
