package com.ticket.venue.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.ticket.shared.exception.CommonErrorCode;
import com.ticket.shared.exception.InvalidRequestException;
import com.ticket.testsupport.persistence.InfraReadRepositoryTestSupport;
import com.ticket.venue.api.VenueLookupApi;
import com.ticket.venue.api.VenueSeatLookupApi;
import com.ticket.venue.api.VenueSeatSnapshot;
import com.ticket.venue.api.VenueSnapshot;
import com.ticket.venue.domain.Region;
import com.ticket.venue.domain.Seat;
import com.ticket.venue.domain.Venue;
import com.ticket.venue.exception.VenueNotFoundException;

/**
 * venue 공개 계약({@link VenueLookupApi}, {@link VenueSeatLookupApi})의 실제 DB 동작을 고정한다.
 *
 * <p>위임만 하던 {@code VenueLookupService}·{@code VenueSeatLookupService}가 사라지고 Aggregate별 adapter 둘이 두 계약을 직접 구현하면서, 그
 * service들이 갖고 있던 빈 입력 처리와 지역 인자 null 검사도 여기로 왔다. 계약을 부르는 쪽에서 보이는 동작이 그대로인지를 본다. 테스트는 구현 클래스가 아니라 계약 타입을 주입받으므로, 두 계약을
 * 한 빈이 구현하게 된 뒤에도 보는 것은 달라지지 않는다.
 */
@SuppressWarnings("NonAsciiCharacters")
class VenueLookupServiceTest extends InfraReadRepositoryTestSupport {
    @Autowired
    private VenueLookupApi venueLookup;

    @Autowired
    private VenueSeatLookupApi venueSeatLookup;

    @Test
    void 존재하는_venue의_표시값을_반환한다() throws Exception {
        final Venue venue = persistVenue("올림픽홀", Region.SEOUL);
        flushAndClear();

        final VenueSnapshot summary = venueLookup.getVenueSnapshot(venue.getId());

        assertThat(summary.venueId()).isEqualTo(venue.getId());
        assertThat(summary.name()).isEqualTo("올림픽홀");
        assertThat(summary.region()).isEqualTo(new VenueSnapshot.RegionView("SEOUL", "서울"));
        assertThat(summary.seatMapLayout().viewBoxWidth()).isEqualTo(1000);
        assertThat(summary.seatMapLayout().viewBoxHeight()).isEqualTo(800);
        assertThat(summary.seatMapLayout().seatDiameter()).isEqualTo(12.0);
    }

    @Test
    void 없는_venueId를_get하면_VenueNotFoundException을_던진다() {
        assertThatThrownBy(() -> venueLookup.getVenueSnapshot(999_999L))
                .isInstanceOf(VenueNotFoundException.class)
                .satisfies(thrown -> {
                    final VenueNotFoundException exception = (VenueNotFoundException) thrown;
                    assertThat(exception.getErrorCode()).isEqualTo(CommonErrorCode.E404);
                    assertThat(exception.getData()).asString().contains("id=999999");
                });
    }

    @Test
    void 배치_조회는_요청한_id만_담고_없는_id는_조용히_빠진다() throws Exception {
        final Venue seoul = persistVenue("올림픽홀", Region.SEOUL);
        final Venue busan = persistVenue("벡스코", Region.GYEONGSANG);
        flushAndClear();

        final Map<Long, VenueSnapshot> summaries =
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

        assertThat(venueLookup.findIdsByRegion("SEOUL")).containsExactly(seoul.getId());
    }

    @Test
    void 공연장이_없는_지역은_빈_집합이다() throws Exception {
        persistVenue("올림픽홀", Region.SEOUL);
        flushAndClear();

        assertThat(venueLookup.findIdsByRegion("JEJU")).isEmpty();
    }

    /**
     * 옛 {@code VenueLookupService}의 {@code Objects.requireNonNull}을 그대로 옮겼다 — 실제로 던지는 것은
     * {@code NullPointerException}이다. {@link VenueLookupApi}의 Javadoc은 {@code IllegalArgumentException}이라고 적고 있지만 이번
     * 리팩터링에서 동작을 바꾸지 않고 실측대로 고정한다.
     */
    @Test
    void 지역_인자가_null이면_거부한다() {
        assertThatThrownBy(() -> venueLookup.findIdsByRegion(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("regionCode must not be null");
    }

    /** 코드 판정은 값 집합을 소유한 venue의 몫이다 - 호출하는 module은 지역 코드 목록을 알지 못한다. */
    @Test
    void 알_수_없는_지역_코드면_invalid_request_예외를_던진다() {
        assertThatThrownBy(() -> venueLookup.findIdsByRegion("NOWHERE")).isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void 요청한_좌석의_주소만_반환한다() throws Exception {
        final Venue venue = persistVenue("올림픽홀", Region.SEOUL);
        final Seat a1 = persistSeat(venue, "A", "1", "1", 1);
        final Seat a2 = persistSeat(venue, "A", "1", "2", 1);
        persistSeat(venue, "B", "1", "1", 2);
        flushAndClear();

        final List<VenueSeatSnapshot> addresses =
                venueSeatLookup.findSeats(venue.getId(), Set.of(a1.getId(), a2.getId()));

        assertThat(addresses).extracting(VenueSeatSnapshot::seatId).containsExactlyInAnyOrder(a1.getId(), a2.getId());
        assertThat(addresses).extracting(VenueSeatSnapshot::section).containsOnly("A");
    }

    @Test
    void 다른_venue의_좌석은_id가_맞아도_반환하지_않는다() throws Exception {
        final Venue venue = persistVenue("올림픽홀", Region.SEOUL);
        final Venue other = persistVenue("벡스코", Region.GYEONGSANG);
        final Seat otherSeat = persistSeat(other, "A", "1", "1", 1);
        flushAndClear();

        assertThat(venueSeatLookup.findSeats(venue.getId(), Set.of(otherSeat.getId())))
                .isEmpty();
    }

    @Test
    void 빈_seatIds는_빈_목록이다() throws Exception {
        final Venue venue = persistVenue("올림픽홀", Region.SEOUL);
        flushAndClear();

        assertThat(venueSeatLookup.findSeats(venue.getId(), Set.of())).isEmpty();
    }

    @Test
    void venue의_모든_좌석_배치_좌표를_반환한다() throws Exception {
        final Venue venue = persistVenue("올림픽홀", Region.SEOUL);
        persistSeat(venue, "A", "1", "1", 1);
        persistSeat(venue, "A", "1", "2", 1);
        persistSeat(persistVenue("벡스코", Region.GYEONGSANG), "A", "1", "1", 1);
        flushAndClear();

        final List<VenueSeatSnapshot> layouts = venueSeatLookup.findAllSeatLayouts(venue.getId());

        assertThat(layouts).hasSize(2);
        assertThat(layouts).extracting(VenueSeatSnapshot::x).containsOnly(10.0);
        assertThat(layouts).extracting(VenueSeatSnapshot::y).containsOnly(20.0);
    }

    @Test
    void 좌석이_없는_venue는_빈_목록이다() throws Exception {
        final Venue venue = persistVenue("빈공연장", Region.SEOUL);
        flushAndClear();

        assertThat(venueSeatLookup.findAllSeatLayouts(venue.getId())).isEmpty();
    }
}
