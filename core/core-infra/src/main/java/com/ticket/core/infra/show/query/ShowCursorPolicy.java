package com.ticket.core.infra.show.query;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.ticket.core.app.error.ApplicationErrorType;
import com.ticket.core.app.show.query.model.ShowCursor;
import com.ticket.core.infra.show.query.ShowSortSupport.SortOrder;
import com.ticket.support.error.CoreException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;

import static com.ticket.core.domain.show.model.QShow.show;

/**
 * 커서 위치를 SQL 조건으로 바꾸고, 마지막 행에서 다음 커서 위치를 만든다.
 *
 * <p>커서의 wire 표현(Base64 문자열)은 core-api가 소유한다. 여기서는 타입 값만 다룬다.
 */
@Component
public class ShowCursorPolicy {

    public void applyCursor(final BooleanBuilder where, final ShowCursor cursor, final SortOrder sortOrder) {
        if (cursor == null) {
            return;
        }
        try {
            validateCursorMatchesRequest(cursor, sortOrder);
            where.and(cursorCondition(cursor, sortOrder));
        } catch (IllegalArgumentException | DateTimeParseException ex) {
            throw new CoreException(ApplicationErrorType.INVALID_INPUT, "cursor 형식이 올바르지 않습니다.");
        }
    }

    public ShowCursor buildNextPosition(final List<Tuple> rows, final int size, final SortOrder sortOrder) {
        final Tuple lastRow = rows.get(size - 1);
        final Long lastId = lastRow.get(show.id);
        final String lastValue = resolveLastValue(lastRow, sortOrder);
        return new ShowCursor(sortOrder.key(), sortOrder.direction().name(), lastValue, lastId);
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
                yield show.createdAt.lt(last).or(show.createdAt.eq(last).and(show.id.lt(lastId)));
            }
            case SHOW_START_APPROACHING -> {
                final LocalDate last = LocalDate.parse(cursor.lastValue());
                yield show.startDate.gt(last).or(show.startDate.eq(last).and(show.id.gt(lastId)));
            }
            case SALE_START_APPROACHING -> {
                final LocalDateTime last = LocalDateTime.parse(cursor.lastValue());
                yield show.saleStartDate.gt(last).or(show.saleStartDate.eq(last).and(show.id.gt(lastId)));
            }
        };
    }

    private String resolveLastValue(final Tuple lastRow, final SortOrder sortOrder) {
        return switch (sortOrder.key()) {
            case POPULAR -> String.valueOf(lastRow.get(show.viewCount));
            case LATEST -> lastRow.get(show.createdAt).toString();
            case SHOW_START_APPROACHING -> lastRow.get(show.startDate).toString();
            case SALE_START_APPROACHING -> lastRow.get(show.saleStartDate).toString();
        };
    }
}
