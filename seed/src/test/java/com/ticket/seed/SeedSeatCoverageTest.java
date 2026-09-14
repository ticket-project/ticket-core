package com.ticket.seed;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import com.ticket.seed.support.AppSchema;

/**
 * 모든 공용 공연장에 물리 좌석이 있고, 모든 공용 회차에 그 공연장의 좌석이 연결되는지를 고정한다.
 *
 * <p>과거 실패를 그대로 재현한다. 공용 SQL의 좌석 복제({@code CROSS JOIN VENUES})는 <b>실행 시점에 존재하는</b> VENUES만 대상으로
 * 삼는데, KOPIS 수집 도구가 새 공연장을 파일 끝에 이어 붙였다. 그렇게 추가된 공연장은 좌석을 하나도 받지 못했고 그 공연장의 회차에는 회차좌석이 생기지 않았다. 개수
 * 비교만으로는 잡히지 않았다 — 기대값 자체를 "이미 만들어진 좌석"에서 계산했기 때문에 기대값과 실제값이 사이좋게 0이었다.
 *
 * <p>테스트 SQL은 {@code minimal-curated.sql}의 마커(@seed-splice) 위치를 기준으로 만든다. 마커 계약이 깨지면 여기서도 깨진다.
 */
@SuppressWarnings("NonAsciiCharacters")
class SeedSeatCoverageTest {
    private static final String VENUE_MARKER = "-- @seed-splice: venues";
    private static final String PERFORMANCE_MARKER = "-- @seed-splice: performances";
    private static final String NEW_VENUE =
            "INSERT INTO VENUES (id, name, address, region, address_detail, zip_code, latitude,"
                    + " longitude, phone, image_url, view_box_width, view_box_height, seat_diameter,"
                    + " gap_x, gap_y, created_at, created_by) VALUES (3, '새로 수집한 공연장', '서울특별시',"
                    + " 'SEOUL', '3관', '00003', 37.5, 127.0, '02-000-0000', NULL, 500, 356, 4.8, 2.5,"
                    + " 2.5, '2026-01-01 10:00:00', 'KOPIS_SEED');";
    private static final String NEW_SHOW =
            "INSERT INTO SHOWS (id, title, sub_title, info, start_date, end_date, view_count,"
                    + " display_sale_type, display_sale_starts_at, display_sale_ends_at, image,"
                    + " venue_id, running_minutes, performer_id, created_at, created_by) VALUES (3,"
                    + " '새로 수집한 공연', '부제', '설명', '2026-08-01', '2026-08-01', 100, 'GENERAL',"
                    + " '2026-07-12 10:00:00', '2026-08-01 13:00:00', NULL, 3, 90, 1,"
                    + " '2026-01-01 10:00:00', 'KOPIS_SEED');";
    private static final String NEW_PERFORMANCE =
            "INSERT INTO PERFORMANCES (id, show_id, performance_no, start_time, end_time,"
                    + " order_open_time, order_close_time, max_can_hold_count, hold_time, created_at,"
                    + " created_by) VALUES (4, 3, 1, '2026-08-01 19:00:00', '2026-08-01 20:30:00',"
                    + " '2026-07-12 10:00:00', '2026-08-01 18:00:00', 4, 600, '2026-01-01 10:00:00',"
                    + " 'KOPIS_SEED');";

    @TempDir Path tempDir;

    private String jdbcUrl;
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void prepareSchema() {
        jdbcUrl =
                AppSchema.createIn(
                        tempDir, "coverage-" + UUID.randomUUID().toString().substring(0, 8));
        jdbcTemplate = new JdbcTemplate(dataSource(jdbcUrl));
    }

    @Test
    void 마커_앞에_추가한_공연장은_좌석과_회차좌석을_받는다() {
        final Path sqlPath =
                write(
                        "with-new-venue.sql",
                        spliceBeforeVenueMarker(
                                spliceBeforePerformanceMarker(minimalSeedSql(), NEW_PERFORMANCE),
                                NEW_VENUE + System.lineSeparator() + NEW_SHOW));

        assertThat(runSeed(sqlPath)).as("새 공연장을 추가해도 정상 적재된다").isZero();

        assertThat(count("SEATS", "venue_id = 3")).as("새 공연장도 좌석 템플릿을 복제받는다").isEqualTo(3L);
        assertThat(count("PERFORMANCE_SEATS", "performance_id = 4"))
                .as("새 회차에도 그 공연장 좌석이 연결된다")
                .isEqualTo(3L);
        assertThat(venuesWithoutSeats()).isZero();
        assertThat(performancesWithoutSeats()).isZero();
    }

    @Test
    void 마커_뒤에_추가한_공연장은_적재를_시작하기_전에_실패한다() {
        final Path sqlPath =
                write(
                        "venue-appended-at-end.sql",
                        minimalSeedSql()
                                + System.lineSeparator()
                                + NEW_VENUE
                                + System.lineSeparator()
                                + NEW_SHOW
                                + System.lineSeparator());

        assertThat(runSeed(sqlPath)).as("순서가 어긋난 SQL은 실패로 알린다").isEqualTo(1);

        assertThat(count("VENUES")).as("적재를 시작하지 않으므로 아무것도 남지 않는다").isZero();
        assertThat(count("SEATS")).isZero();
    }

    /**
     * 순서 검사를 통과하더라도(공연장이 마커 앞에 있다) 좌석이 실제로 만들어지지 않으면 커밋하면 안 된다. 여기서는 복제 조건에서 VENUE 3을 빼 그 상황을 만든다 —
     * {@link CuratedSeedVerifier}가 커밋 전에 잡아야 한다.
     */
    @Test
    void 좌석이_없는_공연장이_남으면_커밋하지_않고_되돌린다() {
        final String excludedFromReplication =
                spliceBeforeVenueMarker(
                                spliceBeforePerformanceMarker(minimalSeedSql(), NEW_PERFORMANCE),
                                NEW_VENUE + System.lineSeparator() + NEW_SHOW)
                        .replace(
                                "WHERE t.venue_id = 1 AND v.id <> 1;",
                                "WHERE t.venue_id = 1 AND v.id <> 1 AND v.id <> 3;");
        final Path sqlPath = write("venue-without-seats.sql", excludedFromReplication);

        assertThat(runSeed(sqlPath)).as("좌석 없는 공연장이 남으면 실패로 알린다").isEqualTo(1);

        assertThat(count("VENUES")).as("적재 트랜잭션 전체가 되돌아간다").isZero();
        assertThat(count("PERFORMANCE_SEATS")).isZero();
    }

    private long venuesWithoutSeats() {
        return count(
                "VENUES v",
                "v.id < "
                        + LoadTestFixtureSeeder.ID_BASE
                        + " AND NOT EXISTS (SELECT 1 FROM SEATS s WHERE s.venue_id = v.id)");
    }

    private long performancesWithoutSeats() {
        return count(
                "PERFORMANCES p",
                "p.id < "
                        + LoadTestFixtureSeeder.ID_BASE
                        + " AND NOT EXISTS (SELECT 1 FROM PERFORMANCE_SEATS ps"
                        + " WHERE ps.performance_id = p.id)");
    }

    private static String spliceBeforeVenueMarker(final String sql, final String block) {
        return spliceBefore(sql, VENUE_MARKER, block);
    }

    private static String spliceBeforePerformanceMarker(final String sql, final String block) {
        return spliceBefore(sql, PERFORMANCE_MARKER, block);
    }

    private static String spliceBefore(final String sql, final String marker, final String block) {
        final int index = sql.indexOf(marker);
        assertThat(index).as("테스트 SQL에 마커 '%s'가 있어야 한다", marker).isNotNegative();
        return sql.substring(0, index)
                + block
                + System.lineSeparator()
                + System.lineSeparator()
                + sql.substring(index);
    }

    private static String minimalSeedSql() {
        try {
            return Files.readString(SeedTestPaths.minimalSeedSql(), StandardCharsets.UTF_8);
        } catch (final IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private Path write(final String name, final String content) {
        final Path path = tempDir.resolve(name);
        try {
            Files.writeString(path, content, StandardCharsets.UTF_8);
        } catch (final IOException exception) {
            throw new UncheckedIOException(exception);
        }
        return path;
    }

    private int runSeed(final Path sqlPath) {
        final Map<String, String> properties = new HashMap<>();
        properties.put("seed.project-dir", SeedTestPaths.projectDir().toString());
        properties.put("seed.sql-path", sqlPath.toString());
        properties.put("seed.jdbc-url", jdbcUrl);
        properties.put("seed.load-test-fixture.performance-count", "0");
        properties.put("seed.load-test-members.count", "0");
        final Map<String, String> previous = SeedSystemProperties.set(properties);
        try {
            return SeedLocalMain.execute();
        } finally {
            SeedSystemProperties.restore(previous);
        }
    }

    private static DataSource dataSource(final String jdbcUrl) {
        return new DriverManagerDataSource(jdbcUrl, "sa", "");
    }

    private long count(final String from, final String where) {
        final Long count =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM " + from + (where == null ? "" : " WHERE " + where),
                        Long.class);
        return count == null ? 0L : count;
    }

    private long count(final String from) {
        return count(from, null);
    }
}
