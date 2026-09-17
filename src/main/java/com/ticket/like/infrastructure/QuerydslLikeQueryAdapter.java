package com.ticket.like.infrastructure;

import static com.ticket.like.domain.QLike.like;

import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.ticket.like.api.LikeEntry;
import com.ticket.like.api.LikeType;
import com.ticket.like.application.port.LikeQueryPort;
import com.ticket.shared.api.CursorPage;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class QuerydslLikeQueryAdapter implements LikeQueryPort {
    private final JPAQueryFactory queryFactory;

    @Override
    public CursorPage<LikeEntry, Long> findLiked(
            final LikeType likeType,
            final Long memberId,
            final @Nullable Long cursorLikeId,
            final int size) {
        final BooleanBuilder where = new BooleanBuilder();
        where.and(like.memberId.eq(memberId));
        where.and(like.likeType.eq(likeType));

        if (cursorLikeId != null) {
            where.and(like.id.lt(cursorLikeId));
        }

        final List<Tuple> rows =
                queryFactory
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

        final List<LikeEntry> items = pageRows.stream().map(this::mapRow).toList();

        final @Nullable Long nextPosition =
                hasNext ? pageRows.get(pageRows.size() - 1).get(like.id) : null;
        return new CursorPage<>(items, hasNext, nextPosition);
    }

    private LikeEntry mapRow(final Tuple tuple) {
        // LIKES의 id·target_id·created_at은 모두 NOT NULL 컬럼이라 같은 행에서 항상 값이 있다.
        return new LikeEntry(
                Objects.requireNonNull(tuple.get(like.id), "like.id"),
                Objects.requireNonNull(tuple.get(like.targetId), "like.targetId"),
                Objects.requireNonNull(tuple.get(like.createdAt), "like.createdAt"));
    }
}
