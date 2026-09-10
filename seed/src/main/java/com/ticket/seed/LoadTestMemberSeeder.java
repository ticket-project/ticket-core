package com.ticket.seed;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 부하 테스트용 회원을 만든다.
 *
 * <p>예전에는 이 일을 애플리케이션 안의 {@code SeedLoadTestMembersUseCase}(member module의
 * {@code @NamedInterface("seed")})가 맡았다. 시드를 앱 밖으로 분리하면서 회원 엔티티·저장소를
 * 쓰지 않고 {@code MEMBERS}에 직접 INSERT한다.
 *
 * <p><b>인증 호환성은 비밀번호 저장 형식 하나에 달려 있다.</b> 앱의
 * {@code member.security.infrastructure.SecurityConfig}가 등록하는 {@code PasswordEncoder}는
 * {@link PasswordEncoderFactories#createDelegatingPasswordEncoder()}다 — 여기서도 같은 factory를
 * 그대로 쓴다. 그래서 저장되는 값은 앱이 만드는 것과 같은 {@code {bcrypt}$2a$...} 형식이고,
 * 로그인 시 {@code PasswordHasher.matches}가 그대로 통과한다. 검증을 통과시키려고 형식을
 * 단순화하거나 해싱을 건너뛰지 않는다.
 *
 * <p>이메일 규칙 {@code loadtest{n}@test.com}은 형제 저장소 {@code gatling-test}의
 * {@code loginEmailPrefix} 기본값과 같다. 이미 있는(탈퇴하지 않은) 이메일은 건너뛴다 —
 * {@code MEMBERS.email}에 unique 제약이 있어 중복 INSERT는 실패한다.
 */
final class LoadTestMemberSeeder implements SeedTask {

    static final String EMAIL_PREFIX = "loadtest";
    static final String EMAIL_SUFFIX = "@test.com";

    private static final String CREATED_BY = "LOAD_TEST_SEED";
    private static final String ROLE = "MEMBER";
    private static final int BATCH_SIZE = 500;

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final int memberCount;
    private final String rawPassword;

    LoadTestMemberSeeder(
            final JdbcTemplate jdbcTemplate,
            final TransactionTemplate transactionTemplate,
            final int memberCount,
            final String rawPassword
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
        this.memberCount = memberCount;
        this.rawPassword = rawPassword;
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

        final Set<String> existing = findExistingEmails();
        final List<String> emailsToCreate = new ArrayList<>();
        for (int memberNo = 1; memberNo <= requested; memberNo++) {
            final String email = EMAIL_PREFIX + memberNo + EMAIL_SUFFIX;
            if (!existing.contains(email)) {
                emailsToCreate.add(email);
            }
        }

        if (emailsToCreate.isEmpty()) {
            return Outcome.skipped(
                    "요청한 회원 %d명이 모두 이미 있습니다. 중복 생성하지 않습니다.".formatted(requested));
        }

        final PasswordEncoder passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
        final String encodedPassword = passwordEncoder.encode(rawPassword);

        transactionTemplate.executeWithoutResult(status -> insert(emailsToCreate, encodedPassword));

        return Outcome.done("요청 %d명 중 %d명을 새로 만들었습니다(%s1%s ~ %s%d%s)."
                .formatted(requested, emailsToCreate.size(),
                        EMAIL_PREFIX, EMAIL_SUFFIX, EMAIL_PREFIX, requested, EMAIL_SUFFIX));
    }

    /** 이미 있는 부하 테스트 이메일이다. 탈퇴 회원은 이메일이 재작성되므로 활성 회원만 본다. */
    private Set<String> findExistingEmails() {
        return new HashSet<>(jdbcTemplate.queryForList(
                "SELECT email FROM MEMBERS WHERE deleted_at IS NULL AND email LIKE ?",
                String.class,
                EMAIL_PREFIX + "%" + EMAIL_SUFFIX));
    }

    private void insert(final List<String> emails, final String encodedPassword) {
        final Timestamp createdAt = Timestamp.valueOf(LocalDateTime.now());
        final List<Object[]> batch = new ArrayList<>(BATCH_SIZE);

        for (int index = 0; index < emails.size(); index++) {
            final String email = emails.get(index);
            batch.add(new Object[]{
                    email,
                    encodedPassword,
                    email.substring(0, email.indexOf('@')),
                    ROLE,
                    createdAt,
                    CREATED_BY
            });
            if (batch.size() == BATCH_SIZE || index == emails.size() - 1) {
                jdbcTemplate.batchUpdate("""
                        INSERT INTO MEMBERS (email, password, name, role, created_at, created_by)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """, batch);
                batch.clear();
            }
        }
    }
}
