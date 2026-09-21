package com.ticket.like.persistence;

import static com.ticket.like.domain.QLike.like;

import java.util.List;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.ticket.like.api.LikeType;
import com.ticket.like.domain.Like;
import com.ticket.shared.api.CursorPage;

import lombok.RequiredArgsConstructor;

/**
 * 찜 목록 읽기 전용 조회다.
 *
 * <p>커서 위치는 마지막 찜 id다. wire 문자열 변환은 호출하는 module의 {@code endpoint}가 한다.
 *
 * <p>엔티티를 그대로 돌려준다 — 공개 계약({@code like.api.LikeSnapshot})으로의 변환은 {@code LikeQueryService}가
 * 한다({@code docs/readability-guidelines.md} §10-1).
 */
@Repository
@RequiredArgsConstructor
public class LikeQuerydslRepository {
    private final JPAQueryFactory queryFactory;

    public CursorPage<Like, Long> findLiked(
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

        final List<Like> rows =
                queryFactory
                        .selectFrom(like)
                        .where(where)
                        .orderBy(like.id.desc())
                        .limit(size + 1L)
                        .fetch();

        if (rows.isEmpty()) {
            return CursorPage.empty();
        }

        final boolean hasNext = rows.size() > size;
        final List<Like> pageRows = hasNext ? rows.subList(0, size) : rows;

        final @Nullable Long nextPosition =
                hasNext ? pageRows.get(pageRows.size() - 1).getId() : null;
        return new CursorPage<>(pageRows, hasNext, nextPosition);
    }
}
