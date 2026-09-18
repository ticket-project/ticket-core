package com.ticket.archfixture.left.usecase;

import com.ticket.archfixture.left.persistence.FixtureRepositoryAdapter;

/** 위반 사례: use case가 저장 adapter를 직접 부른다. 조회 Repository를 열어도 여기는 여전히 막힌다. */
public class StorageAdapterUsingFixtureUseCase {
    private final FixtureRepositoryAdapter adapter;

    public StorageAdapterUsingFixtureUseCase(final FixtureRepositoryAdapter adapter) {
        this.adapter = adapter;
    }

    public Object execute() {
        return adapter.save("fixture");
    }
}
