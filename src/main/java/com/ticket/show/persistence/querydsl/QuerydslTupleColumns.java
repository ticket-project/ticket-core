package com.ticket.show.persistence.querydsl;

import java.util.Objects;

import com.querydsl.core.Tuple;
import com.querydsl.core.types.Expression;

/**
 * Querydsl {@code Tuple.get}이 돌려주는 값의 null 계약을 좁힌다.
 *
 * <p>{@code Tuple.get}은 "그 slot이 projection에 없을 수 있다"는 이유로 언제나 nullable이다. 하지만 바로 위에서 select 한 컬럼을
 * 다시 꺼내는 자리에서는 slot이 반드시 있고, 컬럼 자체가 NOT NULL이면 값도 반드시 있다. 그 사실을 호출마다 {@code
 * Objects.requireNonNull}로 적으면 어느 컬럼을 꺼내는지가 잡음에 묻힌다.
 *
 * <p><b>nullable한 컬럼에는 쓰지 않는다.</b> {@code SHOWS.venue_id}(공연장 없는 공연)와 {@code
 * display_sale_starts_at}/{@code display_sale_ends_at}(표시 정책 미구성)은 실제로 비어 있을 수 있고, 여기에 쓰면 오늘 성공하던
 * 조회가 예외가 된다.
 */
final class QuerydslTupleColumns {
    private QuerydslTupleColumns() {}

    static <T> T required(final Tuple tuple, final Expression<T> column) {
        return Objects.requireNonNull(tuple.get(column), () -> column + "은 NOT NULL 컬럼이다");
    }
}
