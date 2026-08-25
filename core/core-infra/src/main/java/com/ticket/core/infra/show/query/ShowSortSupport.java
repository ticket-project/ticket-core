package com.ticket.core.infra.show.query;

import com.querydsl.core.types.OrderSpecifier;
import com.ticket.core.domain.show.meta.ShowSortKey;
import lombok.Getter;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import static com.ticket.core.domain.show.model.QShow.show;

@Getter
@Component
public class ShowSortSupport {

    public record SortOrder(ShowSortKey key, Sort.Direction direction) {
    }

    public SortOrder resolveSortOrder(final String sort) {
        final ShowSortKey sortKey = ShowSortKey.fromApiValue(sort);
        final Sort.Direction direction = switch (sortKey) {
            case POPULAR, LATEST -> Sort.Direction.DESC;
            case SHOW_START_APPROACHING, SALE_START_APPROACHING -> Sort.Direction.ASC;
        };
        return new SortOrder(sortKey, direction);
    }

    public OrderSpecifier<?> primaryOrderSpecifier(final SortOrder sortOrder) {
        return switch (sortOrder.key()) {
            case POPULAR -> show.viewCount.desc();
            case LATEST -> show.createdAt.desc();
            case SHOW_START_APPROACHING -> show.startDate.asc();
            case SALE_START_APPROACHING -> show.saleStartDate.asc();
        };
    }

    public OrderSpecifier<Long> tieBreakerOrder(final SortOrder sortOrder) {
        return sortOrder.direction().isAscending() ? show.id.asc() : show.id.desc();
    }
}
