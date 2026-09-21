package com.ticket.archfixture.left.persistence;

import java.util.List;

import com.querydsl.jpa.impl.JPAQueryFactory;
import com.ticket.archfixture.left.usecase.FixtureRow;

/** 허용 사례: 자기 module의 DB만 Querydsl로 조회하는 조회 Repository다. use case가 직접 불러도 된다. */
public class AllowedFixtureQuerydslRepository {
    private final JPAQueryFactory queryFactory;

    public AllowedFixtureQuerydslRepository(final JPAQueryFactory queryFactory) {
        this.queryFactory = queryFactory;
    }

    public List<FixtureRow> findAll() {
        return queryFactory == null ? List.of() : List.of();
    }
}
