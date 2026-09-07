package com.ticket.bootstrap.migration;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ADR 0006 "Performance의 책임 혼재" A2: booking V6가 PERFORMANCES의 정책 컬럼 4개와
 * PERFORMANCE_QUEUE_POLICIES(__root V2가 만든, 옛 show/공통 소유 legacy schema)를
 * BOOKING_PERFORMANCE_SALES_POLICIES로 손실 없이 backfill하고 구 schema를 제거하는지 검증한다.
 *
 * <p>{@link BookingModuleSlicingSchemaTest}·{@link BookingModuleMigrationTest}와 같은 기법이다 —
 * {@code __root}와 {@code booking}의 migration만(다른 module 없이) 실제로 적용한다.
 * PERFORMANCE_QUEUE_POLICIES에 backfill 대상 데이터를 미리 넣어야 하는데 그 table 자체는 __root
 * V2가 만들므로, {@code __root}만 먼저 적용해 table을 만든 뒤 데이터를 넣고, 그다음 {@code booking}을
 * 적용하는 두 단계로 나눈다(단일 호출로는 데이터를 끼워 넣을 시점이 없다).
 */
class BookingPerformanceSalesPolicyMigrationTest {

    @Test
    void 둘_다_구성된_회차와_대기열_정책_없는_회차를_손실_없이_backfill하고_구_schema를_제거한다() throws Exception {
        final String url = databaseUrl("happy-path");
        createLegacyBaselineSchema(url);
        ModulithFlywayTestSupport.migrateRootOnly(url);

        try (Connection connection = ModulithFlywayTestSupport.connect(url);
             Statement statement = connection.createStatement()) {
            // 대기열 정책이 있는 회차
            statement.execute("""
                    INSERT INTO performances (id, order_open_time, order_close_time, max_can_hold_count, hold_time)
                    VALUES (1, '2026-05-01 10:00:00', '2026-06-01 10:00:00', 4, 600)
                    """);
            statement.execute("""
                    INSERT INTO performance_queue_policies (performance_id, queue_mode, queue_level, preopen_queue_start_at, waiting_room_message, reason, created_at, created_by)
                    VALUES (1, 'AUTO', 'LEVEL_1', '2026-05-30 10:00:00', '대기 중입니다', '오픈 직후 대기열', CURRENT_TIMESTAMP, 'test')
                    """);
            // 대기열 정책이 없는 회차
            statement.execute("""
                    INSERT INTO performances (id, order_open_time, order_close_time, max_can_hold_count, hold_time)
                    VALUES (2, '2026-05-01 10:00:00', '2026-06-01 10:00:00', NULL, 600)
                    """);
        }

        ModulithFlywayTestSupport.migrate(url, List.of("booking"));

        try (Connection connection = ModulithFlywayTestSupport.connect(url)) {
            assertThat(ModulithFlywayTestSupport.tableExists(connection, "BOOKING_PERFORMANCE_SALES_POLICIES")).isTrue();
            assertThat(ModulithFlywayTestSupport.tableExists(connection, "PERFORMANCE_QUEUE_POLICIES")).isFalse();
            assertThat(hasColumn(connection, "PERFORMANCES", "ORDER_OPEN_TIME")).isFalse();
            assertThat(hasColumn(connection, "PERFORMANCES", "ORDER_CLOSE_TIME")).isFalse();
            assertThat(hasColumn(connection, "PERFORMANCES", "MAX_CAN_HOLD_COUNT")).isFalse();
            assertThat(hasColumn(connection, "PERFORMANCES", "HOLD_TIME")).isFalse();

            try (Statement statement = connection.createStatement()) {
                final ResultSet count = statement.executeQuery("SELECT COUNT(*) FROM BOOKING_PERFORMANCE_SALES_POLICIES");
                count.next();
                assertThat(count.getInt(1)).isEqualTo(2);

                final ResultSet withQueue = statement.executeQuery(
                        "SELECT max_hold_seat_count, hold_duration_seconds, queue_mode, queue_level, waiting_room_message, queue_policy_reason, version "
                                + "FROM BOOKING_PERFORMANCE_SALES_POLICIES WHERE performance_id = 1");
                withQueue.next();
                assertThat(withQueue.getInt("max_hold_seat_count")).isEqualTo(4);
                assertThat(withQueue.getLong("hold_duration_seconds")).isEqualTo(600);
                assertThat(withQueue.getString("queue_mode")).isEqualTo("AUTO");
                assertThat(withQueue.getString("queue_level")).isEqualTo("LEVEL_1");
                assertThat(withQueue.getString("waiting_room_message")).isEqualTo("대기 중입니다");
                assertThat(withQueue.getString("queue_policy_reason")).isEqualTo("오픈 직후 대기열");
                assertThat(withQueue.getLong("version")).isEqualTo(0);

                final ResultSet withoutQueue = statement.executeQuery(
                        "SELECT max_hold_seat_count, queue_mode FROM BOOKING_PERFORMANCE_SALES_POLICIES WHERE performance_id = 2");
                withoutQueue.next();
                assertThat(withoutQueue.getObject("max_hold_seat_count")).isNull();
                assertThat(withoutQueue.getObject("queue_mode")).isNull();
            }
        }
    }

    @Test
    void 접수_기간이_모두_null인_회차는_정책_row를_만들지_않는다() throws Exception {
        final String url = databaseUrl("both-null-skip");
        createLegacyBaselineSchema(url);
        ModulithFlywayTestSupport.migrateRootOnly(url);

        try (Connection connection = ModulithFlywayTestSupport.connect(url);
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO performances (id, order_open_time, order_close_time, max_can_hold_count, hold_time)
                    VALUES (1, NULL, NULL, NULL, NULL)
                    """);
        }

        ModulithFlywayTestSupport.migrate(url, List.of("booking"));

        try (Connection connection = ModulithFlywayTestSupport.connect(url);
             Statement statement = connection.createStatement()) {
            final ResultSet count = statement.executeQuery("SELECT COUNT(*) FROM BOOKING_PERFORMANCE_SALES_POLICIES");
            count.next();
            assertThat(count.getInt(1)).isEqualTo(0);
        }
    }

    @Test
    void 접수_시작만_있고_마감이_null이면_migration이_실패한다() throws Exception {
        final String url = databaseUrl("one-null-fail");
        createLegacyBaselineSchema(url);
        ModulithFlywayTestSupport.migrateRootOnly(url);

        try (Connection connection = ModulithFlywayTestSupport.connect(url);
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO performances (id, order_open_time, order_close_time, max_can_hold_count, hold_time)
                    VALUES (1, '2026-05-01 10:00:00', NULL, NULL, 600)
                    """);
        }

        assertThatThrownBy(() -> ModulithFlywayTestSupport.migrate(url, List.of("booking")))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void 접수_시작이_마감보다_늦으면_migration이_실패한다() throws Exception {
        final String url = databaseUrl("invalid-window-fail");
        createLegacyBaselineSchema(url);
        ModulithFlywayTestSupport.migrateRootOnly(url);

        try (Connection connection = ModulithFlywayTestSupport.connect(url);
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO performances (id, order_open_time, order_close_time, max_can_hold_count, hold_time)
                    VALUES (1, '2026-06-01 10:00:00', '2026-05-01 10:00:00', NULL, 600)
                    """);
        }

        assertThatThrownBy(() -> ModulithFlywayTestSupport.migrate(url, List.of("booking")))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void hold_time이_null이면_600초_기본값을_적용한다() throws Exception {
        final String url = databaseUrl("null-hold-time-default");
        createLegacyBaselineSchema(url);
        ModulithFlywayTestSupport.migrateRootOnly(url);

        try (Connection connection = ModulithFlywayTestSupport.connect(url);
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO performances (id, order_open_time, order_close_time, max_can_hold_count, hold_time)
                    VALUES (1, '2026-05-01 10:00:00', '2026-06-01 10:00:00', NULL, NULL)
                    """);
        }

        ModulithFlywayTestSupport.migrate(url, List.of("booking"));

        try (Connection connection = ModulithFlywayTestSupport.connect(url);
             Statement statement = connection.createStatement()) {
            final ResultSet row = statement.executeQuery(
                    "SELECT hold_duration_seconds FROM BOOKING_PERFORMANCE_SALES_POLICIES WHERE performance_id = 1");
            row.next();
            assertThat(row.getLong("hold_duration_seconds")).isEqualTo(600);
        }
    }

    /**
     * 정책 컬럼 자체가 없는 최소 legacy baseline({@link BookingModuleSlicingSchemaTest}와 같은
     * PERFORMANCES 형태)에서는 이 migration이 예외 없이 no-op으로 끝나야 한다 — 그래야
     * BookingModuleSlicingSchemaTest/BookingTicketSlicingSchemaTest/OracleMigrationCompatibilityTest가
     * 계속 통과한다.
     */
    @Test
    void 정책_컬럼이_없는_최소_baseline에서는_예외_없이_no_op이다() throws Exception {
        final String url = databaseUrl("minimal-baseline-noop");
        try (Connection connection = ModulithFlywayTestSupport.connect(url);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE performances (id BIGINT PRIMARY KEY)");
            statement.execute("CREATE TABLE seats (id BIGINT PRIMARY KEY)");
            statement.execute(
                    "CREATE TABLE performance_seats (" +
                    "  id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, " +
                    "  performance_id BIGINT NOT NULL, " +
                    "  seat_id BIGINT NOT NULL, " +
                    "  state VARCHAR(255), " +
                    "  price DECIMAL(38, 2), " +
                    "  created_at TIMESTAMP NOT NULL, " +
                    "  created_by VARCHAR(255) NOT NULL, " +
                    "  updated_at TIMESTAMP, " +
                    "  updated_by VARCHAR(255), " +
                    "  CONSTRAINT legacy_fk_performance FOREIGN KEY (performance_id) REFERENCES performances, " +
                    "  CONSTRAINT legacy_fk_seat FOREIGN KEY (seat_id) REFERENCES seats" +
                    ")");
            statement.execute("CREATE TABLE order_seats (order_id BIGINT NOT NULL)");
        }

        ModulithFlywayTestSupport.migrate(url, List.of("booking"));

        try (Connection connection = ModulithFlywayTestSupport.connect(url)) {
            assertThat(ModulithFlywayTestSupport.tableExists(connection, "BOOKING_PERFORMANCE_SALES_POLICIES")).isTrue();
            try (Statement statement = connection.createStatement()) {
                final ResultSet count = statement.executeQuery("SELECT COUNT(*) FROM BOOKING_PERFORMANCE_SALES_POLICIES");
                count.next();
                assertThat(count.getInt(1)).isEqualTo(0);
            }
        }
    }

    private boolean hasColumn(final Connection connection, final String table, final String column) throws SQLException {
        try (ResultSet columns = connection.getMetaData().getColumns(null, null, table, column)) {
            return columns.next();
        }
    }

    /**
     * PERFORMANCES/SEATS/PERFORMANCE_SEATS/ORDER_SEATS는 어떤 Flyway migration도 만들지 않는
     * pre-Flyway baseline이다(docs/operations.md 참고, {@link BookingModuleSlicingSchemaTest}와 같은
     * 형태). PERFORMANCES에는 이번에 정리 대상인 정책 컬럼 4개를 추가로 갖는다. __root V3
     * (performance_seats 유니크 인덱스), V4(order_seats 인덱스)가 요구하는 최소 baseline도 함께
     * 갖춘다. PERFORMANCE_QUEUE_POLICIES는 여기서 만들지 않는다 — __root V2가 만드는 정식
     * migration 대상이기 때문이다(각 테스트가 root-only 적용 후 데이터를 채운다).
     */
    private void createLegacyBaselineSchema(final String url) throws Exception {
        try (Connection connection = ModulithFlywayTestSupport.connect(url);
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE performances (
                      id BIGINT PRIMARY KEY,
                      order_open_time TIMESTAMP,
                      order_close_time TIMESTAMP,
                      max_can_hold_count INTEGER,
                      hold_time INTEGER
                    )
                    """);
            statement.execute("CREATE TABLE seats (id BIGINT PRIMARY KEY)");
            statement.execute(
                    "CREATE TABLE performance_seats (" +
                    "  id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, " +
                    "  performance_id BIGINT NOT NULL, " +
                    "  seat_id BIGINT NOT NULL, " +
                    "  state VARCHAR(255), " +
                    "  price DECIMAL(38, 2), " +
                    "  created_at TIMESTAMP NOT NULL, " +
                    "  created_by VARCHAR(255) NOT NULL, " +
                    "  updated_at TIMESTAMP, " +
                    "  updated_by VARCHAR(255), " +
                    "  CONSTRAINT legacy_fk_performance FOREIGN KEY (performance_id) REFERENCES performances, " +
                    "  CONSTRAINT legacy_fk_seat FOREIGN KEY (seat_id) REFERENCES seats" +
                    ")");
            statement.execute("CREATE TABLE order_seats (order_id BIGINT NOT NULL)");
        }
    }

    private String databaseUrl(final String name) {
        return "jdbc:h2:mem:booking-performance-sales-policy-migration-" + name
                + ";MODE=Oracle;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE";
    }
}
