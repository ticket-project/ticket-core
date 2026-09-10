/**
 * Seed 기술 모듈: 로컬·운영 기동 시 초기 데이터를 적재하는 시드 러너를 소유한다. Bounded Context가
 * 아니다.
 *
 * <p>{@code SeedDataLoader}는 {@code seed/kopis-curated.sql}을 파싱해 여러 module의 테이블(카테고리,
 * 공연, 회차 등)에 raw SQL로 적재하고, 부하 테스트 회원만 member의
 * {@link com.ticket.member.application.usecase.SeedLoadTestMembersUseCase}에 붙은 type-level
 * {@code @NamedInterface("seed")}를 통해 {@link com.ticket.member.application.usecase.SeedLoadTestMembersUseCase}
 * 를 호출해 만든다(member의 해당 package-info 참고). {@code LoadTestFixtureSeeder}는 로컬 부하
 * 테스트 전용 고정 ID 대역 데이터를 마찬가지로 raw SQL로 적재한다.
 *
 * <p>어느 한 module이 전유하는 데이터가 아니라 여러 module의 테이블을 함께 다루므로, 특정
 * module 소유로 두지 않고 이 module에 둔다.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Seed", allowedDependencies = {"member :: seed"})
package com.ticket.seed;

import com.ticket.member.application.usecase.SeedLoadTestMembersUseCase;

