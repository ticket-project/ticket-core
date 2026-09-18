package com.ticket.archfixture.left.usecase;

import com.querydsl.jpa.impl.JPAQueryFactory;

/** 위반 사례: 조립이 Querydsl을 직접 쓴다. query에서 허용해도 use case에서는 여전히 막힌다. */
public class QuerydslUsingFixtureUseCase {
    private final JPAQueryFactory queryFactory;

    public QuerydslUsingFixtureUseCase(final JPAQueryFactory queryFactory) {
        this.queryFactory = queryFactory;
    }

    public Object execute() {
        return queryFactory;
    }
}
