package com.ticket.seed;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * 공용 시드가 이미 적재됐는지, 아니면 일부만 적재된 상태인지 판정한다.
 *
 * <p>예전 판정은 {@code SELECT COUNT(*) FROM CATEGORIES}가 0인지 하나만 봤다. CATEGORIES는 3행뿐인
 * 첫 테이블이라 그 뒤가 통째로 비어 있어도 "이미 적재됨"으로 읽혔다. 여기서는 시드 SQL의 리터럴
 * INSERT 개수에서 테이블별 기대 행 수를 직접 계산해 전부 비교한다.
 *
 * <ul>
 *   <li>모든 테이블이 비었다 → 적재한다.</li>
 *   <li>모든 테이블이 기대값과 같다 → 건너뛴다(반복 실행해도 중복이 생기지 않는다).</li>
 *   <li>그 밖(일부만 차 있다) → <b>실패한다.</b> 자동으로 지우거나 채워 넣지 않고 어떤 테이블이
 *       어떻게 어긋났는지 출력한다.</li>
 * </ul>
 *
 * <p>모든 집계는 부하 테스트 전용 고정 ID 대역({@code >= 910000000},
 * {@link LoadTestFixtureSeeder#ID_BASE})을 제외한다. 그 데이터는 다른 작업이 소유하므로 공용
 * 시드의 완전성 판정에 섞이면 안 된다.
 */
final class CuratedSeedInventory {

    /** 시드 SQL이 만드는 좌석 등급 코드다. GRADES는 재사용 가능한 코드 테이블이라 행 수로만 본다. */
    private static final String GRADE_CODES = "'VIP', 'R', 'S', 'A'";
    private static final int GRADE_CODE_COUNT = 4;

    private final List<TableInventory> tables;

    private CuratedSeedInventory(final List<TableInventory> tables) {
        this.tables = List.copyOf(tables);
    }

    static CuratedSeedInventory inspect(final JdbcTemplate jdbcTemplate, final CuratedSeedStatements statements) {
        final long performances = statements.literalInsertCount("PERFORMANCES");
        final long seatTemplate = statements.literalInsertCount("SEATS");
        final long venuesBeforeReplication = statements.venueCountBeforeSeatReplication();

        final List<TableInventory> tables = new ArrayList<>();
        tables.add(count(jdbcTemplate, "CATEGORIES", statements.literalInsertCount("CATEGORIES"), null));
        tables.add(count(jdbcTemplate, "GENRES", statements.literalInsertCount("GENRES"), null));
        tables.add(count(jdbcTemplate, "PERFORMERS", statements.literalInsertCount("PERFORMERS"), null));
        tables.add(count(jdbcTemplate, "VENUES", statements.literalInsertCount("VENUES"), curatedIds("id")));
        tables.add(count(jdbcTemplate, "SHOWS", statements.literalInsertCount("SHOWS"), curatedIds("id")));
        tables.add(count(jdbcTemplate, "SHOW_GENRES", statements.literalInsertCount("SHOW_GENRES"), null));
        tables.add(count(jdbcTemplate, "PERFORMANCES", performances, curatedIds("id")));
        // 좌석 템플릿(VENUE 1)은 리터럴로 들어가고, 복제 INSERT가 그 시점의 나머지 VENUE에 같은
        // 수만큼 복제한다 -> 템플릿 수 x 복제 시점의 VENUE 수.
        tables.add(count(jdbcTemplate, "SEATS", seatTemplate * venuesBeforeReplication, curatedIds("id")));
        tables.add(count(jdbcTemplate, "BOOKING_PERFORMANCE_SALES_POLICIES",
                performances, curatedIds("performance_id")));
        tables.add(gradeCodes(jdbcTemplate));
        // PERFORMANCE_GRADES INSERT는 파일 마지막에 있어 모든 회차에 등급 4개를 붙인다.
        tables.add(count(jdbcTemplate, "PERFORMANCE_GRADES",
                performances * GRADE_CODE_COUNT, curatedIds("performance_id")));
        tables.add(performanceSeats(jdbcTemplate));

        return new CuratedSeedInventory(tables);
    }

    List<TableInventory> tables() {
        return tables;
    }

    Decision decision() {
        if (tables.stream().allMatch(table -> table.state() == TableState.EMPTY)) {
            return Decision.LOAD;
        }
        if (tables.stream().allMatch(table -> table.state() == TableState.COMPLETE)) {
            return Decision.ALREADY_COMPLETE;
        }
        return Decision.PARTIAL;
    }

    /** 사람이 읽을 진단표다. 기대값과 실제값이 어긋난 테이블을 앞세운다. */
    String describe() {
        final StringBuilder builder = new StringBuilder();
        for (final TableInventory table : tables) {
            builder.append(String.format(
                    "    %-38s 기대 %8d / 실제 %8d  %s%n",
                    table.table(), table.expected(), table.actual(), table.state().label()));
        }
        return builder.toString();
    }

    private static String curatedIds(final String column) {
        return column + " < " + LoadTestFixtureSeeder.ID_BASE;
    }

    private static TableInventory count(
            final JdbcTemplate jdbcTemplate,
            final String table,
            final long expected,
            final String where
    ) {
        final String sql = "SELECT COUNT(*) FROM " + table + (where == null ? "" : " WHERE " + where);
        return new TableInventory(table, expected, queryCount(jdbcTemplate, sql));
    }

    private static TableInventory gradeCodes(final JdbcTemplate jdbcTemplate) {
        return new TableInventory(
                "GRADES",
                GRADE_CODE_COUNT,
                queryCount(jdbcTemplate, "SELECT COUNT(*) FROM GRADES WHERE code IN (" + GRADE_CODES + ")")
        );
    }

    /**
     * PERFORMANCE_SEATS는 회차마다 그 회차 공연장의 좌석 수만큼 생긴다. 공연장별 좌석 수가
     * 일정하지 않으므로(파일 뒤쪽 VENUES는 좌석 복제 대상이 아니다) 적재문과 같은 JOIN으로
     * 기대값을 직접 센다.
     */
    private static TableInventory performanceSeats(final JdbcTemplate jdbcTemplate) {
        final long expected = queryCount(jdbcTemplate, """
                SELECT COUNT(*)
                FROM PERFORMANCES p
                JOIN SHOWS sh ON sh.id = p.show_id
                JOIN SEATS st ON st.venue_id = sh.venue_id
                WHERE p.id < %d
                """.formatted(LoadTestFixtureSeeder.ID_BASE));
        final long actual = queryCount(jdbcTemplate,
                "SELECT COUNT(*) FROM PERFORMANCE_SEATS WHERE performance_id < " + LoadTestFixtureSeeder.ID_BASE);
        return new TableInventory("PERFORMANCE_SEATS", expected, actual);
    }

    private static long queryCount(final JdbcTemplate jdbcTemplate, final String sql) {
        final Long count = jdbcTemplate.queryForObject(sql, Long.class);
        return count == null ? 0L : count;
    }

    enum Decision {
        LOAD, ALREADY_COMPLETE, PARTIAL
    }

    enum TableState {
        EMPTY("비었음"), COMPLETE("완전"), PARTIAL("불일치");

        private final String label;

        TableState(final String label) {
            this.label = label;
        }

        String label() {
            return label;
        }
    }

    record TableInventory(String table, long expected, long actual) {

        TableState state() {
            if (actual == 0L) {
                return TableState.EMPTY;
            }
            return actual == expected ? TableState.COMPLETE : TableState.PARTIAL;
        }
    }
}
