package com.ticket.core.infra.showlike.query;
`r`nimport com.ticket.core.app.showlike.query.model.ShowLikeSummaryView;`r`n
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.ticket.core.app.showlike.query.GetMyShowLikesUseCase;
import com.ticket.core.app.showlike.query.ShowLikeReadRepository;
import com.ticket.core.app.support.cursor.CursorPage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

import static com.ticket.core.domain.show.model.QShow.show;
import static com.ticket.core.domain.show.model.QVenue.venue;
import static com.ticket.core.domain.showlike.model.QShowLike.showLike;

@Repository
@RequiredArgsConstructor
public class QuerydslShowLikeReadRepository implements ShowLikeReadRepository {

    private final JPAQueryFactory queryFactory;

    @Override
    public CursorPage<ShowLikeSummaryView, Long> findMyLikedShows(
            final Long memberId,
            final Long cursorLikeId,
            final int size
    ) {
        final BooleanBuilder where = new BooleanBuilder();
        where.and(showLike.member.id.eq(memberId));

        if (cursorLikeId != null) {
            where.and(showLike.id.lt(cursorLikeId));
        }

        final List<Tuple> rows = queryFactory
                .select(
                        showLike.id,
                        show.id,
                        show.title,
                        show.image,
                        show.startDate,
                        show.endDate,
                        venue.name,
                        showLike.createdAt
                )
                .from(showLike)
                .join(showLike.show, show)
                .leftJoin(show.venue, venue)
                .where(where)
                .orderBy(showLike.id.desc())
                .limit(size + 1L)
                .fetch();

        if (rows.isEmpty()) {
            return CursorPage.empty();
        }

        final boolean hasNext = rows.size() > size;
        final List<Tuple> pageRows = hasNext ? rows.subList(0, size) : rows;

        final List<ShowLikeSummaryView> items = pageRows.stream()
                .map(this::mapRow)
                .toList();

        final Long nextPosition = hasNext ? pageRows.get(pageRows.size() - 1).get(showLike.id) : null;
        return new CursorPage<>(items, hasNext, nextPosition);
    }

    private ShowLikeSummaryView mapRow(final Tuple tuple) {
        return new ShowLikeSummaryView(
                tuple.get(show.id),
                tuple.get(show.title),
                tuple.get(show.image),
                tuple.get(show.startDate),
                tuple.get(show.endDate),
                tuple.get(venue.name),
                tuple.get(showLike.createdAt)
        );
    }
}
