package com.ticket.archfixture.left.usecase;

import java.util.List;

import com.ticket.archfixture.left.query.AllowedFixtureQuery;
import com.ticket.archfixture.left.query.FixtureRow;

/** 허용 사례: use case가 구체 Query를 직접 부른다. */
public class FixtureUseCase {
    private final AllowedFixtureQuery query;

    public FixtureUseCase(final AllowedFixtureQuery query) {
        this.query = query;
    }

    public List<FixtureRow> execute() {
        return query.findAll();
    }
}
