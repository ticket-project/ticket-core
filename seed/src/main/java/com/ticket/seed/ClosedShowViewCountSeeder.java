package com.ticket.seed;

import java.time.LocalDateTime;
import java.time.ZoneId;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 예매가 끝난 공용 공연의 조회수를 낮춰 인기순 목록 뒤로 보낸다.
 *
 * <p>인기순 목록은 조회수만 보고 예매 상태는 보지 않는다. 조회수는 앱이 올리지 않는 시드 합성값이라(공용 시드 12,000 이상, 새 수집분 130,000 이상) 끝난 공연도 높은 값을 그대로 갖고 목록
 * 앞에 남는다. 여기서 끝난 공연만 100분의 1(120~2,200)로 줄여 예매 중·오픈 예정 공연이 앞에 오게 한다.
 *
 * <p>끝남 판정은 앱의 표시 상태({@code DisplaySaleWindow.statusAt})와 같다 — 표시 판매 종료가 지났거나 판매 기간이 비어 있으면 끝났다. 시각은 서비스 기준인 한국 시각이다(DB
 * 세션 시간대에 기대지 않는다).
 *
 * <p>{@code view_count >= 10,000}인 행만 바꾸므로 다시 실행해도 이미 낮춘 공연은 그대로다. 부하 픽스처 대역은 건드리지 않는다.
 */
final class ClosedShowViewCountSeeder implements SeedTask {
    static final long LOWERED_BELOW = 10_000L;
    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    ClosedShowViewCountSeeder(final JdbcTemplate jdbcTemplate, final TransactionTemplate transactionTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public String name() {
        return "예매 종료 공연 조회수 낮추기";
    }

    @Override
    public Outcome run() {
        final LocalDateTime now = LocalDateTime.now(SERVICE_ZONE);
        final Integer lowered = transactionTemplate.execute(
                status -> jdbcTemplate.update("""
                        UPDATE SHOWS SET view_count = view_count / 100
                        WHERE id < ?
                          AND view_count >= ?
                          AND (display_sale_starts_at IS NULL OR display_sale_ends_at IS NULL OR display_sale_ends_at < ?)
                        """, LoadTestFixtureSeeder.ID_BASE, LOWERED_BELOW, now));
        if (lowered == null || lowered == 0) {
            return Outcome.skipped("조회수를 낮출 예매 종료 공연이 없습니다.");
        }
        return Outcome.done("예매 종료 공연 %d개의 조회수를 100분의 1로 낮췄습니다(%s 기준).".formatted(lowered, now.withNano(0)));
    }
}
