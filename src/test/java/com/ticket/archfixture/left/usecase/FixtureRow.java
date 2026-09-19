package com.ticket.archfixture.left.usecase;

/** use case가 소유한 응답 record다. endpoint가 이것을 참조하는 것은 허용된다. */
public record FixtureRow(Long id, String name) {}
