package com.ticket.like.preference.infrastructure;

import com.ticket.like.preference.application.port.LikeQueryPort;

import com.ticket.like.LikeType;
import com.ticket.like.preference.application.port.LikeQueryPort;
import com.ticket.like.preference.application.LikeRow;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.ticket.shared.CursorPage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

import static com.ticket.like.preference.domain.QLike.like;

@Repository
@RequiredArgsConstructor
public class QuerydslLikeQueryPort implements LikeQueryPort {

    private final JPAQueryFactory queryFactory;

    @Override
    public CursorPage<LikeRow, Long> findLiked(
            final LikeType likeType,
            final Long memberId,
            final Long cursorLikeId,
            final int size
    ) {
        final BooleanBuilder where = new BooleanBuilder();
        where.and(like.memberId.eq(memberId));
        where.and(like.likeType.eq(likeType));

        if (cursorLikeId != null) {
            where.and(like.id.lt(cursorLikeId));
        }

        final List<Tuple> rows = queryFactory
                .select(like.id, like.targetId, like.createdAt)
                .from(like)
                .where(where)
                .orderBy(like.id.desc())
                .limit(size + 1L)
                .fetch();

        if (rows.isEmpty()) {
            return CursorPage.empty();
        }

        final boolean hasNext = rows.size() > size;
        final List<Tuple> pageRows = hasNext ? rows.subList(0, size) : rows;

        final List<LikeRow> items = pageRows.stream()
                .map(this::mapRow)
                .toList();

        final Long nextPosition = hasNext ? pageRows.get(pageRows.size() - 1).get(like.id) : null;
        return new CursorPage<>(items, hasNext, nextPosition);
    }

    private LikeRow mapRow(final Tuple tuple) {
        return new LikeRow(
                tuple.get(like.id),
                tuple.get(like.targetId),
                tuple.get(like.createdAt)
        );
    }
}
