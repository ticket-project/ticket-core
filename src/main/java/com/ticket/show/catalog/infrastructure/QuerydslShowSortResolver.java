package com.ticket.show.catalog.infrastructure;

import com.querydsl.core.types.OrderSpecifier;
import com.ticket.show.catalog.application.ShowSort;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import static com.ticket.show.catalog.domain.QShow.show;

/**
 * app이 이미 파싱한 {@link ShowSort}를 Querydsl 정렬 방향과 {@code OrderSpecifier}로 바꾼다.
 */
@Component
public class QuerydslShowSortResolver {

    public record SortOrder(ShowSort key, Sort.Direction direction) {
    }

    public SortOrder resolveSortOrder(final ShowSort sort) {
        final Sort.Direction direction = switch (sort) {
            case POPULAR, LATEST -> Sort.Direction.DESC;
            case SHOW_START_APPROACHING, SALE_START_APPROACHING -> Sort.Direction.ASC;
        };
        return new SortOrder(sort, direction);
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
}
