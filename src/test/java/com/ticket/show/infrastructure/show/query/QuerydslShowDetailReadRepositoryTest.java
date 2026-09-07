package com.ticket.show.infrastructure.show.query;

import com.ticket.show.application.show.query.ShowDetailReadRepository;
import com.ticket.show.application.show.query.model.ShowDetailView;
import com.ticket.show.domain.show.BookingStatus;
import com.ticket.show.domain.show.image.ShowCardImagePathConverter;
import com.ticket.show.domain.show.Region;
import com.ticket.show.domain.show.Category;
import com.ticket.show.domain.show.Genre;
import com.ticket.show.domain.show.Show;
import com.ticket.show.domain.show.Performer;
import com.ticket.show.domain.show.Venue;
import com.ticket.core.infra.support.InfraReadRepositoryTestSupport;
import com.ticket.show.domain.grade.Grade;
import com.ticket.show.domain.performance.Performance;
import com.ticket.show.domain.performance.policy.BookingEntryResolver;
import com.ticket.show.domain.queue.QueueLevel;
import com.ticket.show.domain.queue.QueueMode;
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
        QuerydslShowDetailReadRepository.class,
        ShowCardImagePathConverter.class
})
@SuppressWarnings("NonAsciiCharacters")
class QuerydslShowDetailReadRepositoryTest extends InfraReadRepositoryTestSupport {

    @Autowired
    private ShowDetailReadRepository showDetailReadRepository;

    private Long showId;

    @BeforeEach
    void setUp() throws Exception {
        Venue venue = persistVenue("예술의전당", Region.SEOUL);
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
        Performance queuedPerformance = persistPerformance(show, 1L, LocalDate.of(2026, 3, 16).atTime(14, 0));
        queuedPerformance.updateQueuePolicy(QueueMode.FORCE_ON, QueueLevel.LEVEL_1, null, null, null);
        Performance directPerformance = persistPerformance(show, 2L, LocalDate.of(2026, 3, 16).atTime(19, 0));
        directPerformance.updateQueuePolicy(QueueMode.FORCE_OFF, QueueLevel.LEVEL_1, null, null, null);
        // ADR 0005: 가격은 회차(Performance) 단위로만 존재한다. 두 회차에 서로 다른 가격 범위를 둬
        // show 상세의 priceSummary가 회차 전체의 min/max를 파생하는지 확인한다.
        Grade vip = persistGrade("VIP", "VIP석");
        Grade r = persistGrade("R", "R석");
        persistPerformanceGrade(queuedPerformance, vip, BigDecimal.valueOf(150000), 1);
        persistPerformanceGrade(queuedPerformance, r, BigDecimal.valueOf(100000), 2);
        persistPerformanceGrade(directPerformance, vip, BigDecimal.valueOf(180000), 1);
        flushAndClear();
    }

    @Test
    void 공연_상세정보를_조합해_조회하고_예매상태는_Clock_기준으로_계산한다() {
        Optional<ShowDetailView> result = showDetailReadRepository.findShowDetail(showId);

        assertThat(result).isPresent();
        ShowDetailView detail = result.orElseThrow();
        assertThat(detail.title()).isEqualTo("단독 공연");
        assertThat(detail.genreNames()).contains("케이팝");
        assertThat(detail.priceSummary().minPrice()).isEqualByComparingTo("100000");
        assertThat(detail.priceSummary().maxPrice()).isEqualByComparingTo("180000");
        assertThat(detail.performanceDates()).hasSize(1);
        assertThat(detail.performanceDates().getFirst().performances()).hasSize(2);
        assertThat(detail.performanceDates().getFirst().performances().getFirst().entryType())
                .isEqualTo(BookingEntryResolver.EntryType.QUEUE);
        assertThat(detail.performanceDates().getFirst().performances().getFirst().queueRequired()).isTrue();
        assertThat(detail.performanceDates().getFirst().performances().getFirst().queueEnterUrl())
                .isEqualTo("/api/v1/queue/performances/%d/enter".formatted(
                        detail.performanceDates().getFirst().performances().getFirst().id()
                ));
        assertThat(detail.performanceDates().getFirst().performances().get(1).entryType())
                .isEqualTo(BookingEntryResolver.EntryType.DIRECT);
        assertThat(detail.performanceDates().getFirst().performances().get(1).queueRequired()).isFalse();
        assertThat(detail.performanceDates().getFirst().performances().get(1).redirectUrl())
                .isEqualTo("/booking/seat?performanceId=%d".formatted(
                        detail.performanceDates().getFirst().performances().get(1).id()
                ));
        assertThat(detail.image()).isEqualTo("/api/images/shows/card/" + showId + ".jpg");
        assertThat(detail.venue().name()).isEqualTo("예술의전당");
        assertThat(detail.performer().name()).isEqualTo("홍길동");
        assertThat(detail.bookingStatus()).isEqualTo(BookingStatus.ON_SALE);
    }

    @Test
    void 대기열_정책은_PERFORMANCES가_아닌_별도_테이블에_저장된다() {
        Number count = (Number) entityManager
                .createNativeQuery("select count(*) from performance_queue_policies")
                .getSingleResult();

        assertThat(count.longValue()).isEqualTo(2L);
    }

    @Test
    void 존재하지_않는_공연이면_empty를_반환한다() {
        assertThat(showDetailReadRepository.findShowDetail(999999L)).isEmpty();
    }
}
