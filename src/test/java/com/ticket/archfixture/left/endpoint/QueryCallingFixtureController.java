package com.ticket.archfixture.left.endpoint;

import com.ticket.archfixture.left.query.AllowedFixtureQuery;

/** 위반 사례: endpoint가 use case를 건너뛰고 구체 Query를 직접 부른다. */
public class QueryCallingFixtureController {
    private final AllowedFixtureQuery query;

    public QueryCallingFixtureController(final AllowedFixtureQuery query) {
        this.query = query;
    }

    public Object list() {
        return query.findAll();
    }
}
