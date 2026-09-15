package com.ticket.show.infrastructure;

import static com.ticket.show.domain.show.QShow.show;

import java.time.LocalDateTime;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.NumberExpression;
import com.ticket.show.domain.show.SaleDisplayStatus;

/**
 * {@link SaleDisplayStatus} 판정의 유일한 원본은 {@code DisplaySaleWindow.statusAt(now)}다. 여기서는 그 판정과 같은 결론을
 * 내는 Querydsl 조건만 만든다 — null 창(시작·종료 중 하나라도 없음)은 CLOSED로 본다(TD-12: 예전에는 이 null 처리가 도메인 판정과 달라서 필터
 * 결과가 어긋났다).
 */
@Component
public class SaleDisplayStatusPredicates {
    /**
     * 마감(={@code CLOSED})이면 1, 아니면 0. 최신순 정렬에서 마감된 공연을 뒤로 보내는 데 쓴다 (ORDER BY 이 값 ASC -> 마감되지 않은 공연이
     * 먼저).
     *
     * <p>판정은 {@link #condition}의 {@code CLOSED}와 같은 식이다 — 마감 여부를 정렬이 따로 판단하면 필터 결과와 정렬 결과가
     * 어긋난다(TD-12가 남긴 교훈).
     */
    public NumberExpression<Integer> saleClosedRank(final LocalDateTime now) {
        return new CaseBuilder()
                .when(condition(SaleDisplayStatus.CLOSED, now))
                .then(1)
                .otherwise(0);
    }

    /** 상태 필터가 지정되지 않으면(= {@code null}) 조건 없이 전체를 보도록 {@code null}을 돌려준다. */
    public @Nullable BooleanExpression condition(
            final @Nullable SaleDisplayStatus saleDisplayStatus, final LocalDateTime now) {
        if (saleDisplayStatus == null) {
            return null;
        }

        final var startsAt = show.displaySaleWindow.startsAt;
        final var endsAt = show.displaySaleWindow.endsAt;

        return switch (saleDisplayStatus) {
            case BEFORE_OPEN -> startsAt.isNotNull().and(endsAt.isNotNull()).and(startsAt.gt(now));
            case ON_SALE ->
                    startsAt.isNotNull()
                            .and(endsAt.isNotNull())
                            .and(startsAt.loe(now))
                            .and(endsAt.goe(now));
            case CLOSED -> startsAt.isNull().or(endsAt.isNull()).or(endsAt.lt(now));
        };
    }
}
