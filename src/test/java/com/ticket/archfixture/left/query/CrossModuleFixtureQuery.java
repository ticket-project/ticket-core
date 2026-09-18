package com.ticket.archfixture.left.query;

import com.querydsl.jpa.impl.JPAQueryFactory;
import com.ticket.archfixture.right.api.FixtureApi;

/** 위반 사례: 조회가 다른 업무 module의 공개 API를 불러 결과를 조합한다. */
public class CrossModuleFixtureQuery {
    private final JPAQueryFactory queryFactory;
    private final FixtureApi other;

    public CrossModuleFixtureQuery(final JPAQueryFactory queryFactory, final FixtureApi other) {
        this.queryFactory = queryFactory;
        this.other = other;
    }

    public String combine() {
        return other.displayName();
    }
}
