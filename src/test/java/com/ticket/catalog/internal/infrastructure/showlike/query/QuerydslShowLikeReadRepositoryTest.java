package com.ticket.catalog.internal.infrastructure.showlike.query;

import com.ticket.catalog.internal.application.showlike.query.ShowLikeReadRepository;
import com.ticket.catalog.internal.application.showlike.query.model.ShowLikeSummaryView;
import com.ticket.catalog.internal.domain.show.Region;
import com.ticket.catalog.internal.domain.show.Show;
import com.ticket.catalog.internal.domain.show.Venue;
import com.ticket.core.infra.support.InfraReadRepositoryTestSupport;
import com.ticket.member.internal.domain.member.model.Member;
import com.ticket.shared.CursorPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@Import(QuerydslShowLikeReadRepository.class)
@SuppressWarnings("NonAsciiCharacters")
class QuerydslShowLikeReadRepositoryTest extends InfraReadRepositoryTestSupport {

    @Autowired
    private ShowLikeReadRepository showLikeReadRepository;

    private Long memberId;

    @BeforeEach
    void 초기_데이터를_설정한다() throws Exception {
        Member member = persistMember("user@example.com", "홍길동");
        memberId = member.getId();

        Venue venue = persistVenue("서울홀", Region.SEOUL);
        Show show1 = persistShow("첫 공연", venue, null, 10L, LocalDateTime.now().minusDays(5), LocalDateTime.now().plusDays(5));
        Show show2 = persistShow("두번째 공연", venue, null, 20L, LocalDateTime.now().minusDays(5), LocalDateTime.now().plusDays(5));
        Show show3 = persistShow("세번째 공연", venue, null, 30L, LocalDateTime.now().minusDays(5), LocalDateTime.now().plusDays(5));

        persistShowLike(member, show1);
        persistShowLike(member, show2);
        persistShowLike(member, show3);
        flushAndClear();
    }

    @Test
    void 찜한_공연을_최신순으로_조회한다() {
        CursorPage<ShowLikeSummaryView, Long> result = showLikeReadRepository.findMyLikedShows(memberId, null, 2);

        assertThat(result.items()).extracting(ShowLikeSummaryView::title)
                .containsExactly("세번째 공연", "두번째 공연");
        assertThat(result.nextPosition()).isNotNull();
        assertThat(result.hasNext()).isTrue();
    }

    @Test
    void 커서_이후의_찜한_공연을_조회한다() {
        CursorPage<ShowLikeSummaryView, Long> firstPage = showLikeReadRepository.findMyLikedShows(memberId, null, 1);
        CursorPage<ShowLikeSummaryView, Long> secondPage =
                showLikeReadRepository.findMyLikedShows(memberId, firstPage.nextPosition(), 1);

        assertThat(firstPage.items()).extracting(ShowLikeSummaryView::title)
                .containsExactly("세번째 공연");
        assertThat(secondPage.items()).extracting(ShowLikeSummaryView::title)
                .containsExactly("두번째 공연");
    }

    @Test
    void 찜한_공연이_없으면_빈_슬라이스를_반환한다() {
        CursorPage<ShowLikeSummaryView, Long> result = showLikeReadRepository.findMyLikedShows(-1L, null, 10);

        assertThat(result.items()).isEmpty();
        assertThat(result.hasNext()).isFalse();
        assertThat(result.nextPosition()).isNull();
    }
}
