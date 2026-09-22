package com.ticket.seed;

import java.util.Map;

/**
 * 운영 Oracle에 초기 데이터를 적재하고 종료하는 독립 실행 프로그램이다.
 *
 * <pre>
 *   .\gradlew.bat seedProd
 * </pre>
 *
 * <p><b>테이블 생성과 데이터 적재는 별개다.</b> 이 명령은 이미 준비된 테이블에 데이터를 넣기만 한다 — 테이블을 만들거나 지우거나 초기화하지 않고, 기존의 불완전한 데이터를 자동으로 고치지도 않는다.
 *
 * <p>접속 설정은 {@code SPRING_DATASOURCE_URL} / {@code SPRING_DATASOURCE_USERNAME} / {@code SPRING_DATASOURCE_PASSWORD}
 * 환경변수에서만 온다. 하나라도 없으면 적재를 시작하기 전에 실패한다 — 로컬 DB로 대체하지 않는다. Wallet을 쓰는 경우 서버와 같은 {@code TNS_ADMIN}을 그대로 쓴다.
 *
 * <p>기본 적재 대상은 공연 데이터(공연장·공연자·공연·회차·좌석·등급·가격·예매 정책)뿐이다. 테스트 회원과 부하 테스트 회차는 {@code -Dseed.load-test-members.count} /
 * {@code -Dseed.load-test-fixture.performance-count}를 명시할 때만 만들고, 회원을 만들 때는 {@code SEED_LOAD_TEST_MEMBER_PASSWORD}가 반드시
 * 있어야 한다.
 */
public final class SeedProdMain {
    private SeedProdMain() {}

    public static void main(final String[] args) {
        System.exit(execute());
    }

    /** 테스트가 실제 실행 경로를 그대로 밟을 수 있도록 package 범위로 둔다. */
    static int execute() {
        return execute(System.getenv());
    }

    /** 환경변수를 주입받는다. 테스트는 실제 프로세스 환경을 바꾸지 않고 같은 경로를 밟는다. */
    static int execute(final Map<String, String> environment) {
        return SeedProgram.execute(SeedTarget.PROD, () -> SeedSettings.forProd(environment));
    }
}
