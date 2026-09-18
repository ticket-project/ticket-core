package com.ticket.archfixture.left.query;

import com.ticket.archfixture.left.usecase.FixtureUseCase;

/** 위반 사례: 조회가 조립(use case)을 거꾸로 부른다. */
public class UseCaseCallingFixtureQuery {
    private final FixtureUseCase useCase;

    public UseCaseCallingFixtureQuery(final FixtureUseCase useCase) {
        this.useCase = useCase;
    }

    public Object execute() {
        return useCase;
    }
}
