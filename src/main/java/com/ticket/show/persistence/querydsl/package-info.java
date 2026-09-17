/**
 * show 조회 port({@code *QueryPort})의 Querydsl 구현이다.
 *
 * <p>{@code Querydsl*QueryAdapter}와 그 어댑터들이 공유하는 조건·정렬·projection helper({@code
 * QuerydslShowCursorConditionBuilder}, {@code QuerydslShowSortResolver}, {@code
 * QuerydslTupleColumns}, {@code SaleDisplayStatusPredicates})가 함께 있다. helper를 package-private으로
 * 유지하려면 어댑터와 같은 package에 있어야 한다.
 */
@NullMarked
package com.ticket.show.persistence.querydsl;

import org.jspecify.annotations.NullMarked;
