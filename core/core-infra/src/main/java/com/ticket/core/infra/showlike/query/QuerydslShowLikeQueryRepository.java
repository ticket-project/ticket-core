package com.ticket.core.domain.showlike.query;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.ticket.core.support.cursor.CursorSlice;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.stereotype.Repository;

import java.util.List;

import static com.ticket.core.domain.show.model.QShow.show;
import static com.ticket.core.domain.show.venue.QVenue.venue;
import static com.ticket.core.domain.showlike.model.QShowLike.showLike;

@Repository
@RequiredArgsConstructor
public class ShowLikeQueryRepository {

    private final JPAQueryFactory queryFactory;

    public CursorSlice<GetMyShowLikesUseCase.ShowLikeSummary> findMyLikedShows(
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
            return emptyCursorSlice(size);
        }

        final boolean hasNext = rows.size() > size;
        final List<Tuple> pageRows = hasNext ? rows.subList(0, size) : rows;

        final List<GetMyShowLikesUseCase.ShowLikeSummary> items = pageRows.stream()
                .map(this::mapRow)
                .toList();

        final Slice<GetMyShowLikesUseCase.ShowLikeSummary> slice = new SliceImpl<>(items, PageRequest.of(0, size), hasNext);
        final String nextCursor = hasNext ? String.valueOf(pageRows.get(pageRows.size() - 1).get(showLike.id)) : null;
        return new CursorSlice<>(slice, nextCursor);
    }

    private <T> CursorSlice<T> emptyCursorSlice(final int size) {
        return new CursorSlice<>(new SliceImpl<>(List.of(), PageRequest.of(0, size), false), null);
    }

    private GetMyShowLikesUseCase.ShowLikeSummary mapRow(final Tuple tuple) {
        return new GetMyShowLikesUseCase.ShowLikeSummary(
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
