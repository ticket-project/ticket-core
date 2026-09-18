package com.ticket.venue.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.ticket.testsupport.persistence.InfraReadRepositoryTestSupport;
import com.ticket.venue.api.Region;
import com.ticket.venue.api.VenueLookupApi;
import com.ticket.venue.api.VenueSummary;
import com.ticket.venue.domain.Venue;

/**
 * venue 공개 계약({@link VenueLookupApi})의 실제 DB 동작을 고정한다.
 *
 * <p>위임만 하던 {@code VenueLookupService}가 사라지고 이 조회가 계약을 직접 구현하면서, 그 service가 갖고 있던 빈 입력 처리와 지역 인자
 * null 검사도 여기로 왔다. 계약을 부르는 쪽에서 보이는 동작이 그대로인지를 본다.
 */
@SuppressWarnings("NonAsciiCharacters")
class VenueSummaryQueryTest extends InfraReadRepositoryTestSupport {
    @Autowired private VenueLookupApi venueLookup;

    @Test
    void 존재하는_venue의_표시값을_반환한다() throws Exception {
        final Venue venue = persistVenue("올림픽홀", Region.SEOUL);
        flushAndClear();

        final VenueSummary summary = venueLookup.findSummary(venue.getId()).orElseThrow();

        assertThat(summary.venueId()).isEqualTo(venue.getId());
        assertThat(summary.name()).isEqualTo("올림픽홀");
        assertThat(summary.region()).isEqualTo(Region.SEOUL);
        assertThat(summary.seatMapLayout().viewBoxWidth()).isEqualTo(1000);
        assertThat(summary.seatMapLayout().viewBoxHeight()).isEqualTo(800);
        assertThat(summary.seatMapLayout().seatDiameter()).isEqualTo(12.0);
    }

    @Test
    void 없는_venueId는_예외가_아니라_empty다() {
        assertThat(venueLookup.findSummary(999_999L)).isEmpty();
    }

    @Test
    void 배치_조회는_요청한_id만_담고_없는_id는_조용히_빠진다() throws Exception {
        final Venue seoul = persistVenue("올림픽홀", Region.SEOUL);
        final Venue busan = persistVenue("벡스코", Region.GYEONGSANG);
        flushAndClear();

        final Map<Long, VenueSummary> summaries =
                venueLookup.getSummaries(Set.of(seoul.getId(), busan.getId(), 999_999L));

        assertThat(summaries).containsOnlyKeys(seoul.getId(), busan.getId());
        assertThat(summaries.get(busan.getId()).name()).isEqualTo("벡스코");
    }

    @Test
    void 빈_venueId_집합은_빈_map이다() {
        assertThat(venueLookup.getSummaries(Set.of())).isEmpty();
    }

    @Test
    void 지역으로_venueId를_조회한다() throws Exception {
        final Venue seoul = persistVenue("올림픽홀", Region.SEOUL);
        persistVenue("벡스코", Region.GYEONGSANG);
        flushAndClear();

        assertThat(venueLookup.findIdsByRegion(Region.SEOUL)).containsExactly(seoul.getId());
    }

    @Test
    void 공연장이_없는_지역은_빈_집합이다() throws Exception {
        persistVenue("올림픽홀", Region.SEOUL);
        flushAndClear();

        assertThat(venueLookup.findIdsByRegion(Region.JEJU)).isEmpty();
    }

    /**
     * 옛 {@code VenueLookupService}의 {@code Objects.requireNonNull}을 그대로 옮겼다 — 실제로 던지는 것은 {@code
     * NullPointerException}이다. {@link VenueLookupApi}의 Javadoc은 {@code IllegalArgumentException}이라고
     * 적고 있지만 이번 리팩터링에서 동작을 바꾸지 않고 실측대로 고정한다.
     */
    @Test
    void 지역_인자가_null이면_거부한다() {
        assertThatThrownBy(() -> venueLookup.findIdsByRegion(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("region must not be null");
    }
}
