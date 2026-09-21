package com.ticket.archfixture.left.endpoint;

import com.ticket.archfixture.left.persistence.AllowedFixtureQuerydslRepository;

/** 위반 사례: endpoint가 use case를 건너뛰고 구체 조회 Repository를 직접 부른다. */
public class QueryCallingFixtureController {
    private final AllowedFixtureQuerydslRepository queryRepository;

    public QueryCallingFixtureController(final AllowedFixtureQuerydslRepository queryRepository) {
        this.queryRepository = queryRepository;
    }

    public Object list() {
        return queryRepository.findAll();
    }
}
