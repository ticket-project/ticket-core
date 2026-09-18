package com.ticket.archfixture.left.usecase;

import java.util.List;

import com.ticket.archfixture.left.persistence.AllowedFixtureQueryRepository;
import com.ticket.archfixture.left.query.FixtureRow;

/** 허용 사례: use case가 같은 module의 조회 Repository를 직접 부른다. */
public class FixtureUseCase {
    private final AllowedFixtureQueryRepository queryRepository;

    public FixtureUseCase(final AllowedFixtureQueryRepository queryRepository) {
        this.queryRepository = queryRepository;
    }

    public List<FixtureRow> execute() {
        return queryRepository.findAll();
    }
}
