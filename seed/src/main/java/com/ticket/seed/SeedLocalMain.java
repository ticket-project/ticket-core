package com.ticket.seed;

import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 로컬 H2 DB에 초기 데이터를 적재하고 종료하는 독립 실행 프로그램이다.
 *
 * <pre>
 *   .\gradlew.bat seedLocal
 * </pre>
 *
 * <p>접속 설정의 원본은 {@code src/main/resources/application-local.yml}이고, 부하 테스트 회원·회차를 기본으로 함께 만든다. 운영
 * Oracle에 넣는 명령은 {@link SeedProdMain}이다. 실행 본체는 둘이 공유한다({@link SeedProgram}).
 *
 * <p>실행 순서와 사전 조건은 {@code seed/README.md}가 원본이다.
 */
public final class SeedLocalMain {
    private SeedLocalMain() {}

    public static void main(final String[] args) {
        System.exit(execute());
    }

    /** 테스트가 실제 실행 경로를 그대로 밟을 수 있도록 package 범위로 둔다. */
    static int execute() {
        return execute(System.getenv());
    }

    static int execute(final Map<String, String> environment) {
        return SeedProgram.execute(SeedTarget.LOCAL, () -> SeedSettings.forLocal(environment));
    }

    /** 실행 순서를 고정하는 테스트가 쓴다. 실제 목록은 {@link SeedProgram#tasks}가 만든다. */
    static List<SeedTask> tasks(
            final JdbcTemplate jdbcTemplate,
            final TransactionTemplate transactionTemplate,
            final SeedSettings settings) {
        return SeedProgram.tasks(jdbcTemplate, transactionTemplate, settings);
    }
}
