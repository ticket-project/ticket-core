package com.ticket.show.catalog.infrastructure;

import com.ticket.show.catalog.application.port.ShowDetailQueryPort;

import com.ticket.show.catalog.application.port.ShowDetailQueryPort;
import com.ticket.show.catalog.application.ShowDetailView;
import com.ticket.show.catalog.domain.SaleDisplayStatus;
import com.ticket.show.catalog.domain.ShowCardImagePathConverter;
import com.ticket.venue.Region;
import com.ticket.show.classification.domain.Category;
import com.ticket.show.classification.domain.Genre;
import com.ticket.show.catalog.domain.Show;
import com.ticket.show.performer.domain.Performer;
import com.ticket.venue.domain.Venue;
import com.ticket.core.infra.support.InfraReadRepositoryTestSupport;
import com.ticket.show.performance.domain.Grade;
import com.ticket.show.performance.domain.Performance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@Import({
        QuerydslShowDetailQueryPort.class,
        ShowCardImagePathConverter.class
})
@SuppressWarnings("NonAsciiCharacters")
class QuerydslShowDetailQueryPortTest extends InfraReadRepositoryTestSupport {

    @Autowired
    private ShowDetailQueryPort showDetailQueryPort;

    private Long showId;
    private Long venueId;

    @BeforeEach
    void setUp() throws Exception {
        Venue venue = persistVenue("예술의전당", Region.SEOUL);
        venueId = venue.getId();
        Performer performer = persistPerformer("홍길동");
        Category category = persistCategory("CONCERT", "콘서트");
        Genre genre = persistGenre("KPOP", "케이팝", category);
        Show show = persistShow(
                "단독 공연",
                venue,
                performer,
                321L,
                LocalDateTime.of(2026, 3, 10, 0, 0),
                LocalDateTime.of(2026, 3, 20, 23, 59)
        );
        showId = show.getId();
        entityManager.createNativeQuery("update shows set image = :image where id = :id")
                .setParameter("image", "/api/images/shows/" + showId + ".png")
                .setParameter("id", showId)
                .executeUpdate();
        persistShowGenre(show, genre);
        Performance firstPerformance = persistPerformance(show, 1L, LocalDate.of(2026, 3, 16).atTime(14, 0));
        Performance secondPerformance = persistPerformance(show, 2L, LocalDate.of(2026, 3, 16).atTime(19, 0));
        // ADR 0005: 가격은 회차(Performance) 단위로만 존재한다. 두 회차에 서로 다른 가격 범위를 둬
        // show 상세의 priceSummary가 회차 전체의 min/max를 파생하는지 확인한다.
        Grade vip = persistGrade("VIP", "VIP석");
        Grade r = persistGrade("R", "R석");
        persistPerformanceGrade(firstPerformance, vip, BigDecimal.valueOf(150000), 1);
        persistPerformanceGrade(firstPerformance, r, BigDecimal.valueOf(100000), 2);
        persistPerformanceGrade(secondPerformance, vip, BigDecimal.valueOf(180000), 1);
        flushAndClear();
    }

    @Test
    void 공연_상세정보를_조합해_조회하고_예매상태는_Clock_기준으로_계산한다() {
        Optional<ShowDetailView> result = showDetailQueryPort.findShowDetail(showId);

        assertThat(result).isPresent();
        ShowDetailView detail = result.orElseThrow();
        assertThat(detail.title()).isEqualTo("단독 공연");
        assertThat(detail.genreNames()).contains("케이팝");
        assertThat(detail.priceSummary().minPrice()).isEqualByComparingTo("100000");
        assertThat(detail.priceSummary().maxPrice()).isEqualByComparingTo("180000");
        assertThat(detail.performanceDates()).hasSize(1);
        assertThat(detail.performanceDates().getFirst().performances()).hasSize(2);
        assertThat(detail.performanceDates().getFirst().performances().getFirst().performanceNo()).isEqualTo(1L);
        assertThat(detail.performanceDates().getFirst().performances().get(1).performanceNo()).isEqualTo(2L);
        assertThat(detail.image()).isEqualTo("/api/images/shows/card/" + showId + ".jpg");
        assertThat(detail.venueId()).isEqualTo(venueId);
        assertThat(detail.performer().name()).isEqualTo("홍길동");
        assertThat(detail.saleDisplayStatus()).isEqualTo(SaleDisplayStatus.ON_SALE);
    }

    @Test
    void 존재하지_않는_공연이면_empty를_반환한다() {
        assertThat(showDetailQueryPort.findShowDetail(999999L)).isEmpty();
    }
}
