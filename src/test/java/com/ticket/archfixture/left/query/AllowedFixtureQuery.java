package com.ticket.archfixture.left.query;

import java.util.List;

import com.querydsl.jpa.impl.JPAQueryFactory;

/** 허용 사례: 자기 module의 DB만 Querydsl로 조회한다. */
public class AllowedFixtureQuery {
    private final JPAQueryFactory queryFactory;

    public AllowedFixtureQuery(final JPAQueryFactory queryFactory) {
        this.queryFactory = queryFactory;
    }

    public List<FixtureRow> findAll() {
        return queryFactory == null ? List.of() : List.of();
    }
}
