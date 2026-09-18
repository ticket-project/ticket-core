package com.ticket.show.query;

import static com.ticket.show.domain.show.QShow.show;
import static com.ticket.show.query.QuerydslTupleColumns.required;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.NumberExpression;
import com.ticket.shared.exception.InvalidRequestException;
import com.ticket.show.domain.show.DisplaySaleWindow;
import com.ticket.show.domain.show.SaleDisplayStatus;
import com.ticket.show.query.QuerydslShowSortResolver.SortOrder;

import lombok.RequiredArgsConstructor;

/**
 * 커서 위치를 SQL 조건으로 바꾸고, 마지막 행에서 다음 커서 위치를 만든다.
 *
 * <p>커서의 wire 표현(Base64 문자열)은 {@code com.ticket.show.endpoint.cursor.ShowCursorCodec}이 소유한다. 여기서는
 * 타입 값만 다룬다.
 *
 * <p>{@link ShowSort#LATEST}는 정렬 키가 셋이라 조건도 셋이 겹친다. 조건은 ORDER BY와 <b>같은 순서·같은 방향</b>이어야 페이지 사이에
 * 중복·누락이 생기지 않는다.
 */
@Component
@RequiredArgsConstructor
public class QuerydslShowCursorConditionBuilder {
    private final QuerydslShowSortResolver sortResolver;

    public void applyCursor(
            final BooleanBuilder where,
            final @Nullable ShowCursor cursor,
            final SortOrder sortOrder) {
        if (cursor == null) {
            return;
        }
        try {
            validateCursorMatchesRequest(cursor, sortOrder);
            where.and(cursorCondition(cursor, sortOrder));
        } catch (IllegalArgumentException | DateTimeParseException ex) {
            throw new InvalidRequestException("cursor 형식이 올바르지 않습니다.");
        }
    }

    public ShowCursor buildNextPosition(
            final List<Tuple> rows, final int size, final SortOrder sortOrder) {
        final Tuple lastRow = rows.get(size - 1);
        // show.id는 이 projection에 항상 들어 있는 PK라 조회된 행에서는 값이 비어 있을 수 없다.
        final Long lastId = required(lastRow, show.id);
        final String lastValue = resolveLastValue(lastRow, sortOrder);
        if (!ShowSort.LATEST.equals(sortOrder.key())) {
            return new ShowCursor(sortOrder.key(), sortOrder.direction().name(), lastValue, lastId);
        }
        // 위에서 최신순이 아니면 이미 반환했고, 최신순 SortOrder는 판정 시각을 반드시 갖는다.
        final LocalDateTime evaluatedAt =
                Objects.requireNonNull(
                        sortOrder.saleClosedEvaluatedAt(), "최신순 SortOrder에 마감 판정 시각이 없습니다.");
        return new ShowCursor(
                sortOrder.key(),
                sortOrder.direction().name(),
                lastValue,
                lastId,
                saleClosedRankOf(lastRow, evaluatedAt),
                evaluatedAt.toString());
    }

    /**
     * 마지막 행의 마감 여부다. 판정은 {@link DisplaySaleWindow#statusAt}이 한다 — SQL 쪽 {@code CASE}와 같은 규칙이어야 커서
     * 경계가 정렬과 어긋나지 않는다.
     */
    private int saleClosedRankOf(final Tuple lastRow, final LocalDateTime evaluatedAt) {
        final DisplaySaleWindow window =
                new DisplaySaleWindow(
                        lastRow.get(show.displaySaleWindow.startsAt),
                        lastRow.get(show.displaySaleWindow.endsAt));
        return SaleDisplayStatus.CLOSED.equals(window.statusAt(evaluatedAt)) ? 1 : 0;
    }

    private void validateCursorMatchesRequest(final ShowCursor cursor, final SortOrder sortOrder) {
        if (!sortOrder.key().equals(cursor.sort())) {
            throw new IllegalArgumentException("cursor.sort와 요청 sort가 일치하지 않습니다.");
        }
        if (!sortOrder.direction().name().equalsIgnoreCase(cursor.dir())) {
            throw new IllegalArgumentException("cursor.dir와 요청 dir가 일치하지 않습니다.");
        }
        if (cursor.lastId() == null) {
            throw new IllegalArgumentException("cursor.lastId가 없습니다.");
        }
        if (!StringUtils.hasText(cursor.lastValue())) {
            throw new IllegalArgumentException("cursor.lastValue가 없습니다.");
        }
        if (ShowSort.LATEST.equals(sortOrder.key()) && cursor.saleClosedRank() == null) {
            // 최신순 정렬이 마감 여부를 먼저 보기 전에 발급된 커서다. 그 커서로 이어 읽으면
            // 마감 그룹 경계를 무시하고 등록일만으로 잘라 중복·누락이 생긴다.
            throw new IllegalArgumentException("cursor.saleClosedRank가 없습니다.");
        }
    }

    private BooleanExpression cursorCondition(final ShowCursor cursor, final SortOrder sortOrder) {
        final Long lastId = cursor.lastId();
        return switch (sortOrder.key()) {
            case POPULAR -> {
                final long last = Long.parseLong(cursor.lastValue());
                yield show.viewCount.lt(last).or(show.viewCount.eq(last).and(show.id.lt(lastId)));
            }
            case LATEST -> {
                final LocalDateTime last = LocalDateTime.parse(cursor.lastValue());
                final NumberExpression<Integer> rank = sortResolver.saleClosedRank(sortOrder);
                // LATEST 커서는 위 validate에서 saleClosedRank가 있는 것만 통과한다.
                final int lastRank = Objects.requireNonNull(cursor.saleClosedRank());
                final BooleanExpression afterWithinSameRank =
                        show.createdAt.lt(last).or(show.createdAt.eq(last).and(show.id.lt(lastId)));
                // ORDER BY: 마감 여부 ASC -> 등록일 DESC -> id DESC. 조건도 같은 순서로 겹친다.
                yield rank.gt(lastRank).or(rank.eq(lastRank).and(afterWithinSameRank));
            }
            case SHOW_START_APPROACHING -> {
                final LocalDate last = LocalDate.parse(cursor.lastValue());
                yield show.startDate.gt(last).or(show.startDate.eq(last).and(show.id.gt(lastId)));
            }
            case SALE_START_APPROACHING -> {
                final LocalDateTime last = LocalDateTime.parse(cursor.lastValue());
                yield show.displaySaleWindow
                        .startsAt
                        .gt(last)
                        .or(show.displaySaleWindow.startsAt.eq(last).and(show.id.gt(lastId)));
            }
        };
    }

    private String resolveLastValue(final Tuple lastRow, final SortOrder sortOrder) {
        return switch (sortOrder.key()) {
            case POPULAR -> String.valueOf(required(lastRow, show.viewCount));
            case LATEST -> required(lastRow, show.createdAt).toString();
            // 이 정렬의 키가 startDate라 마지막 행에는 값이 있다.
            case SHOW_START_APPROACHING ->
                    Objects.requireNonNull(lastRow.get(show.startDate)).toString();
            // 이 정렬은 판매 시작이 있는 행만 대상으로 하므로 마지막 행에도 값이 있다.
            case SALE_START_APPROACHING ->
                    Objects.requireNonNull(lastRow.get(show.displaySaleWindow.startsAt)).toString();
        };
    }
}
