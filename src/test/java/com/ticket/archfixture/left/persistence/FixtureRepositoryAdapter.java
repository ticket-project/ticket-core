package com.ticket.archfixture.left.persistence;

/** 저장 adapter다. 이름도 필드도 조회 Repository가 아니므로 use case에 열리지 않는다. */
public class FixtureRepositoryAdapter {
    public Object save(final Object entity) {
        return entity;
    }
}
