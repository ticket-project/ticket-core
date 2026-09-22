package com.ticket.seed;

import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 공용 시드가 만든 데이터의 관계가 실제로 성립하는지 <b>커밋 전에</b> 확인한다.
 *
 * <p>개수 비교({@link CuratedSeedInventory})만으로는 부족하다. 기대 행 수 자체를 "이미 만들어진 좌석"에서 계산하면, 좌석이 아예 없는 공연장은 기대값에서도 빠져 기대값과 실제값이
 * 사이좋게 0이 된다 — 누락이 정상 적재로 보인다. 여기서는 개수가 아니라 <b>관계</b>를 본다: 모든 공용 공연장에 물리 좌석이 있는지, 모든 공용 회차에 그 공연장의 좌석이 연결됐는지.
 *
 * <p>{@link CuratedSeedLoader}의 트랜잭션 안에서 실행한다. 위반이 하나라도 있으면 예외를 던져 적재 전체를 되돌린다 — 누락된 상태가 커밋돼 "정상 완료"로 보고되는 일이 없어야 한다.
 *
 * <p>모든 검사는 부하 테스트 전용 고정 ID 대역({@link LoadTestFixtureSeeder#ID_BASE} 이상)을 제외한다. 그 데이터는 다른 작업이 소유한다.
 */
final class CuratedSeedVerifier {
    private CuratedSeedVerifier() {}

    static void verify(final JdbcTemplate jdbcTemplate) {
        final long base = LoadTestFixtureSeeder.ID_BASE;
        final List<Violation> violations = new ArrayList<>();

        check(jdbcTemplate, violations, "물리 좌석이 없는 공연장", """
                SELECT COUNT(*) FROM VENUES v
                WHERE v.id < %d
                  AND NOT EXISTS (SELECT 1 FROM SEATS s WHERE s.venue_id = v.id)
                """.formatted(base));

        check(jdbcTemplate, violations, "회차좌석이 하나도 없는 회차", """
                SELECT COUNT(*) FROM PERFORMANCES p
                WHERE p.id < %d
                  AND NOT EXISTS (SELECT 1 FROM PERFORMANCE_SEATS ps WHERE ps.performance_id = p.id)
                """.formatted(base));

        check(jdbcTemplate, violations, "그 회차 공연장의 좌석이 아닌 회차좌석", """
                SELECT COUNT(*)
                FROM PERFORMANCE_SEATS ps
                JOIN PERFORMANCES p ON p.id = ps.performance_id
                JOIN SHOWS sh ON sh.id = p.show_id
                JOIN SEATS st ON st.id = ps.seat_id
                WHERE p.id < %d AND st.venue_id <> sh.venue_id
                """.formatted(base));

        check(jdbcTemplate, violations, "공연장 좌석 수와 회차좌석 수가 다른 회차", """
                SELECT COUNT(*) FROM (
                  SELECT p.id
                  FROM PERFORMANCES p
                  JOIN SHOWS sh ON sh.id = p.show_id
                  JOIN (SELECT venue_id, COUNT(*) seat_count FROM SEATS GROUP BY venue_id) vs
                    ON vs.venue_id = sh.venue_id
                  LEFT JOIN PERFORMANCE_SEATS ps ON ps.performance_id = p.id
                  WHERE p.id < %d
                  GROUP BY p.id, vs.seat_count
                  HAVING COUNT(ps.seat_id) <> vs.seat_count
                ) mismatched
                """.formatted(base));

        check(jdbcTemplate, violations, "실재하지 않는 공연장을 가리키는 공연", """
                SELECT COUNT(*) FROM SHOWS sh
                WHERE sh.id < %d
                  AND sh.venue_id IS NOT NULL
                  AND NOT EXISTS (SELECT 1 FROM VENUES v WHERE v.id = sh.venue_id)
                """.formatted(base));

        check(jdbcTemplate, violations, "다른 회차의 등급을 가리키는 회차좌석", """
                SELECT COUNT(*)
                FROM PERFORMANCE_SEATS ps
                LEFT JOIN PERFORMANCE_GRADES pg ON pg.id = ps.performance_grade_id
                WHERE ps.performance_id < %d
                  AND (pg.id IS NULL OR pg.performance_id <> ps.performance_id)
                """.formatted(base));

        check(jdbcTemplate, violations, "등급 가격과 단가가 다른 회차좌석", """
                SELECT COUNT(*)
                FROM PERFORMANCE_SEATS ps
                JOIN PERFORMANCE_GRADES pg ON pg.id = ps.performance_grade_id
                WHERE ps.performance_id < %d AND ps.unit_price <> pg.price
                """.formatted(base));

        check(jdbcTemplate, violations, "예매 판매정책이 없는 회차", """
                SELECT COUNT(*) FROM PERFORMANCES p
                WHERE p.id < %d
                  AND NOT EXISTS (
                    SELECT 1 FROM BOOKING_PERFORMANCE_SALES_POLICIES sp
                    WHERE sp.performance_id = p.id)
                """.formatted(base));

        if (violations.isEmpty()) {
            return;
        }

        final StringBuilder detail = new StringBuilder();
        for (final Violation violation : violations) {
            detail.append("    %-40s %8d건%n".formatted(violation.description(), violation.count()));
        }
        throw new SeedFailure("""
                공용 시드 적재 결과의 관계 검증에 실패했습니다. 이 적재는 커밋하지 않고 전부 되돌립니다.

                %s
                필요한 조치
                  1. seed/sql/kopis-curated.sql의 문장 순서를 확인하세요. 좌석 복제(CROSS JOIN VENUES)보다
                     뒤에 선언된 공연장은 물리 좌석을 받지 못합니다.
                  2. 새 데이터는 '-- @seed-splice: venues' / '-- @seed-splice: performances' 마커 앞에
                     넣습니다.
                """.formatted(detail.toString()));
    }

    private static void check(
            final JdbcTemplate jdbcTemplate,
            final List<Violation> violations,
            final String description,
            final String sql) {
        final Long count = jdbcTemplate.queryForObject(sql, Long.class);
        if (count != null && count > 0) {
            violations.add(new Violation(description, count));
        }
    }

    private record Violation(String description, long count) {}
}
