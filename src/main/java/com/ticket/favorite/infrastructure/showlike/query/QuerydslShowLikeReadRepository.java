package com.ticket.favorite.infrastructure.showlike.query;

import com.ticket.favorite.application.showlike.query.ShowLikeReadRepository;
import com.ticket.favorite.application.showlike.query.model.ShowLikeRow;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.ticket.shared.CursorPage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

import static com.ticket.favorite.domain.showlike.model.QShowLike.showLike;

@Repository
@RequiredArgsConstructor
public class QuerydslShowLikeReadRepository implements ShowLikeReadRepository {

    private final JPAQueryFactory queryFactory;

    @Override
    public CursorPage<ShowLikeRow, Long> findLikedShows(
            final Long memberId,
            final Long cursorLikeId,
            final int size
    ) {
        final BooleanBuilder where = new BooleanBuilder();
        where.and(showLike.memberId.eq(memberId));

        if (cursorLikeId != null) {
            where.and(showLike.id.lt(cursorLikeId));
        }

        final List<Tuple> rows = queryFactory
                .select(showLike.id, showLike.showId, showLike.createdAt)
                .from(showLike)
                .where(where)
                .orderBy(showLike.id.desc())
                .limit(size + 1L)
                .fetch();

        if (rows.isEmpty()) {
            return CursorPage.empty();
        }

        final boolean hasNext = rows.size() > size;
        final List<Tuple> pageRows = hasNext ? rows.subList(0, size) : rows;

        final List<ShowLikeRow> items = pageRows.stream()
                .map(this::mapRow)
                .toList();

        final Long nextPosition = hasNext ? pageRows.get(pageRows.size() - 1).get(showLike.id) : null;
        return new CursorPage<>(items, hasNext, nextPosition);
    }

    private ShowLikeRow mapRow(final Tuple tuple) {
        return new ShowLikeRow(
                tuple.get(showLike.id),
                tuple.get(showLike.showId),
                tuple.get(showLike.createdAt)
        );
    }
}
