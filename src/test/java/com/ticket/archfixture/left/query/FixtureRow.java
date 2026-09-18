package com.ticket.archfixture.left.query;

/** query가 소유한 읽기 모델이다. endpoint가 이것을 참조하는 것은 허용된다. */
public record FixtureRow(Long id, String name) {}
