package com.ticket.seed;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.sql.DataSource;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 적재를 시작하기 전에 DB와 스키마가 준비됐는지 확인한다.
 *
 * <p>준비되지 않은 DB에서 적재를 시작하면 실패 원인이 "테이블 없음" 수백 개로 흩어진다. 여기서 먼저 막고, 무엇이 없는지와 무엇을 해야 하는지를 한 번에 알려준다.
 *
 * <p><b>스키마를 만들지 않는다.</b> 테이블 생성·삭제·초기화는 이 프로그램의 범위가 아니다. 로컬은 local 프로파일 기동이, 운영은 배포가 같은 Flyway migration으로 이미 만들어 둔 것을
 * 전제로 한다.
 *
 * <p><b>확인 범위는 실제로 접속한 스키마({@code current_schema()})다.</b> 다른 스키마의 동명 테이블을 "준비됐다"고 읽으면 INSERT는 엉뚱한 곳을 보거나 권한 오류로 실패한다.
 */
final class SeedPreconditions {
    /** 시드가 실제로 INSERT하는 테이블 전체다. 하나라도 없으면 적재를 시작하지 않는다. */
    private static final List<String> REQUIRED_TABLES = List.of(
            "CATEGORIES",
            "GENRES",
            "PERFORMERS",
            "VENUES",
            "SHOWS",
            "SHOW_GENRES",
            "PERFORMANCES",
            "SEATS",
            "GRADES",
            "PERFORMANCE_GRADES",
            "PERFORMANCE_SEATS",
            "BOOKING_PERFORMANCE_SALES_POLICIES",
            "MEMBERS",
            "ORDERS",
            "ORDER_SEATS");

    private SeedPreconditions() {}

    static void verify(final DataSource dataSource, final String jdbcUrl, final SeedTarget target) {
        final JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        final String schema;
        final Set<String> present;
        try {
            schema = jdbcTemplate.queryForObject("SELECT current_schema()", String.class);
            present = new HashSet<>(jdbcTemplate.queryForList(
                    "SELECT upper(table_name) FROM information_schema.tables WHERE table_schema = current_schema()",
                    String.class));
        } catch (final DataAccessException exception) {
            throw new SeedFailure(
                    """
                    DB에 접속할 수 없습니다. url=%s
                      원인: %s
                    %s
                    """.formatted(
                                    SeedConsole.maskedUrl(jdbcUrl),
                                    exception.getMostSpecificCause().getMessage(),
                                    connectionHint(target)),
                    exception);
        }

        final List<String> missing = REQUIRED_TABLES.stream()
                .filter(table -> !present.contains(table))
                .toList();
        if (!missing.isEmpty()) {
            throw new SeedFailure("""
                    시드에 필요한 테이블이 없습니다. url=%s
                      스키마: %s
                      없는 테이블: %s
                    %s
                    """.formatted(
                    SeedConsole.maskedUrl(jdbcUrl), schema, String.join(", ", missing), missingTableHint(target)));
        }
    }

    private static String connectionHint(final SeedTarget target) {
        if (target == SeedTarget.PROD) {
            return """
                      -> SPRING_DATASOURCE_URL / SPRING_DATASOURCE_USERNAME / SPRING_DATASOURCE_PASSWORD와,
                         RDS endpoint·네트워크 접근·TLS 인증서 경로를 확인하세요.""";
        }
        return """
                  -> local 프로파일로 애플리케이션이 기동된 적이 있는지, PostgreSQL 접속 설정이 맞는지 확인하세요.""";
    }

    private static String missingTableHint(final SeedTarget target) {
        if (target == SeedTarget.PROD) {
            return """
                      -> 테이블 생성은 이 명령의 범위가 아닙니다. seedProd는 이미 준비된 테이블에 데이터만 넣습니다.
                         애플리케이션 배포(Flyway migration)로 스키마가 먼저 만들어져야 하고, 접속 계정이
                         그 테이블의 소유자여야 합니다.""";
        }
        return """
                  -> 스키마는 애플리케이션이 만듭니다. local 프로파일로 서버를 먼저 기동해
                     스키마가 만들어진 뒤에 seedLocal을 실행하세요.""";
    }
}
