package com.ticket.seed;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 부하 테스트용 회원을 만든다.
 *
 * <p>예전에는 이 일을 애플리케이션 안의 {@code SeedLoadTestMembersUseCase}(member module의 {@code @NamedInterface("seed")})가 맡았다. 시드를 앱
 * 밖으로 분리하면서 회원 엔티티·저장소를 쓰지 않고 {@code MEMBERS}에 직접 INSERT한다.
 *
 * <p><b>비밀번호는 만들지 않는다.</b> 앱의 가입·로그인은 소셜(OAuth2) 전용이고, 부하 테스트({@code gatling-test})는 로그인 없이 회원 ID를 {@code sub}로 서명한 합성
 * access token을 쓴다. 그래서 여기서 필요한 것은 활성 회원 row와 그 ID뿐이다.
 *
 * <p>이메일 규칙은 {@code loadtest{n}@test.com}이다. 이미 있는(탈퇴하지 않은) 이메일은 건너뛴다 — {@code MEMBERS.email}에 unique 제약이 있어 중복 INSERT는
 * 실패하고, 건너뛰므로 이미 있는 회원의 ID도 바뀌지 않는다. 탈퇴 회원은 이메일이 재작성되므로 활성 회원만 본다.
 */
final class LoadTestMemberSeeder implements SeedTask {
    static final String EMAIL_PREFIX = "loadtest";
    static final String EMAIL_SUFFIX = "@test.com";
    private static final String CREATED_BY = "LOAD_TEST_SEED";
    private static final String ROLE = "MEMBER";
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final int memberCount;

    LoadTestMemberSeeder(
            final JdbcTemplate jdbcTemplate, final TransactionTemplate transactionTemplate, final int memberCount) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
        this.memberCount = memberCount;
    }

    @Override
    public String name() {
        return "부하 테스트 회원 적재";
    }

    @Override
    public Outcome run() {
        final int requested = Math.max(0, memberCount);
        if (requested == 0) {
            return Outcome.skipped("요청 회원 수가 0이라 적재하지 않습니다.");
        }

        final int created = transactionTemplate.execute(status -> jdbcTemplate.update(
                """
                        INSERT INTO MEMBERS (email, name, role, created_at, created_by)
                        SELECT ? || n || ?, ? || n, ?, CURRENT_TIMESTAMP, ?
                        FROM generate_series(1, ?) AS n
                        WHERE NOT EXISTS (
                          SELECT 1 FROM MEMBERS m WHERE m.deleted_at IS NULL AND m.email = ? || n || ?
                        )
                        ORDER BY n
                        """,
                EMAIL_PREFIX,
                EMAIL_SUFFIX,
                EMAIL_PREFIX,
                ROLE,
                CREATED_BY,
                requested,
                EMAIL_PREFIX,
                EMAIL_SUFFIX));
        if (created == 0) {
            return Outcome.skipped("요청한 회원 %d명이 모두 이미 있습니다. 중복 생성하지 않습니다.".formatted(requested));
        }

        return Outcome.done("요청 %d명 중 %d명을 새로 만들었습니다(%s1%s ~ %s%d%s)."
                .formatted(requested, created, EMAIL_PREFIX, EMAIL_SUFFIX, EMAIL_PREFIX, requested, EMAIL_SUFFIX));
    }
}
