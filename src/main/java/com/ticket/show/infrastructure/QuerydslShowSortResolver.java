package com.ticket.show.infrastructure;

import static com.ticket.show.domain.show.QShow.show;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.NumberExpression;
import com.ticket.shared.exception.InvalidRequestException;
import com.ticket.show.application.ShowCursor;
import com.ticket.show.application.ShowSort;

import lombok.RequiredArgsConstructor;

/**
 * app이 이미 파싱한 {@link ShowSort}를 Querydsl 정렬 방향과 {@code OrderSpecifier}로 바꾼다.
 *
 * <p>{@link ShowSort#LATEST}만 정렬 키가 셋이다 — <b>마감되지 않은 공연 먼저, 그 안에서 등록일 내림차순, 등록일이 같으면 id 내림차순</b>.
 * 마감 판정에는 시각이 필요하고, 그 시각은 페이지 사이에 흔들리면 안 되므로({@link ShowCursor} 참고) {@link SortOrder}가 들고 다닌다.
 */
@Component
@RequiredArgsConstructor
public class QuerydslShowSortResolver {
    private final SaleDisplayStatusPredicateFactory saleDisplayStatusPredicateFactory;
    private final Clock clock;

    /**
     * @param saleClosedEvaluatedAt 마감 여부 판정 시각. {@link ShowSort#LATEST}에서만 값이 있다.
     */
    public record SortOrder(
            ShowSort key,
            Sort.Direction direction,
            @Nullable LocalDateTime saleClosedEvaluatedAt) {}

    /**
     * 첫 페이지는 현재 시각으로, 이어지는 페이지는 커서에 적힌 시각으로 마감 여부를 판정한다.
     *
     * @throws InvalidRequestException 최신순인데 커서에 판정 시각이 없거나 형식이 틀릴 때. 정렬 규칙이 바뀌기 전에 발급된 커서가 여기에 걸린다
     *     — 조용히 섞인 순서를 내놓는 것보다 낫다.
     */
    public SortOrder resolveSortOrder(final ShowSort sort, final @Nullable ShowCursor cursor) {
        final Sort.Direction direction =
                switch (sort) {
                    case POPULAR, LATEST -> Sort.Direction.DESC;
                    case SHOW_START_APPROACHING, SALE_START_APPROACHING -> Sort.Direction.ASC;
                };
        return new SortOrder(sort, direction, resolveSaleClosedEvaluatedAt(sort, cursor));
    }

    public SortOrder resolveSortOrder(final ShowSort sort) {
        return resolveSortOrder(sort, null);
    }

    /** ORDER BY에 그대로 넘길 정렬 키 전체다. 페이지 조회와 결과 재조회가 같은 배열을 쓴다. */
    public OrderSpecifier<?>[] orderSpecifiers(final SortOrder sortOrder) {
        if (ShowSort.LATEST.equals(sortOrder.key())) {
            return new OrderSpecifier<?>[] {
                saleClosedRank(sortOrder).asc(), show.createdAt.desc(), show.id.desc()
            };
        }
        return new OrderSpecifier<?>[] {
            primaryOrderSpecifier(sortOrder), tieBreakerOrder(sortOrder)
        };
    }

    public OrderSpecifier<?> primaryOrderSpecifier(final SortOrder sortOrder) {
        return switch (sortOrder.key()) {
            case POPULAR -> show.viewCount.desc();
            case LATEST -> show.createdAt.desc();
            case SHOW_START_APPROACHING -> show.startDate.asc();
            case SALE_START_APPROACHING -> show.displaySaleWindow.startsAt.asc();
        };
    }

    public OrderSpecifier<Long> tieBreakerOrder(final SortOrder sortOrder) {
        return sortOrder.direction().isAscending() ? show.id.asc() : show.id.desc();
    }

    /** 최신순의 첫 정렬 키. 커서 조건도 같은 식을 써야 순서와 페이지 경계가 맞는다. */
    public NumberExpression<Integer> saleClosedRank(final SortOrder sortOrder) {
        final LocalDateTime evaluatedAt = sortOrder.saleClosedEvaluatedAt();
        if (evaluatedAt == null) {
            throw new IllegalStateException(
                    "마감 여부 판정 시각이 없습니다. 최신순이 아닌 정렬에서 호출했습니다: " + sortOrder.key());
        }
        return saleDisplayStatusPredicateFactory.saleClosedRank(evaluatedAt);
    }

    private @Nullable LocalDateTime resolveSaleClosedEvaluatedAt(
            final ShowSort sort, final @Nullable ShowCursor cursor) {
        if (!ShowSort.LATEST.equals(sort)) {
            return null;
        }
        if (cursor == null) {
            return LocalDateTime.now(clock);
        }
        if (cursor.evaluatedAt() == null || cursor.evaluatedAt().isBlank()) {
            throw new InvalidRequestException("cursor 형식이 올바르지 않습니다.");
        }
        try {
            return LocalDateTime.parse(cursor.evaluatedAt());
        } catch (final DateTimeParseException exception) {
            throw new InvalidRequestException("cursor 형식이 올바르지 않습니다.");
        }
    }
}
