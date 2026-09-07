package com.ticket.favorite.infrastructure.showlike.query;

import com.ticket.favorite.application.showlike.query.ShowLikeReadRepository;
import com.ticket.favorite.application.showlike.query.model.ShowLikeRow;
import com.ticket.core.infra.support.InfraReadRepositoryTestSupport;
import com.ticket.member.domain.member.model.Member;
import com.ticket.shared.CursorPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@Import(QuerydslShowLikeReadRepository.class)
@SuppressWarnings("NonAsciiCharacters")
class QuerydslShowLikeReadRepositoryTest extends InfraReadRepositoryTestSupport {

    @Autowired
    private ShowLikeReadRepository showLikeReadRepository;

    private Long memberId;
    private Long showId1;
    private Long showId2;
    private Long showId3;

    @BeforeEach
    void 초기_데이터를_설정한다() throws Exception {
        Member member = persistMember("user@example.com", "홍길동");
        memberId = member.getId();

        showId1 = persistShowLikeFixture(member, "첫 공연");
        showId2 = persistShowLikeFixture(member, "두번째 공연");
        showId3 = persistShowLikeFixture(member, "세번째 공연");
        flushAndClear();
    }

    private Long persistShowLikeFixture(final Member member, final String title) throws Exception {
        var venue = persistVenue(title + " 공연장", com.ticket.show.domain.show.Region.SEOUL);
        var show = persistShow(title, venue, null, 0L,
                java.time.LocalDateTime.now().minusDays(5), java.time.LocalDateTime.now().plusDays(5));
        persistShowLike(member, show);
        return show.getId();
    }

    @Test
    void 찜한_공연을_최신순으로_조회한다() {
        CursorPage<ShowLikeRow, Long> result = showLikeReadRepository.findLikedShows(memberId, null, 2);

        assertThat(result.items()).extracting(ShowLikeRow::showId)
                .containsExactly(showId3, showId2);
        assertThat(result.nextPosition()).isNotNull();
        assertThat(result.hasNext()).isTrue();
    }

    @Test
    void 커서_이후의_찜한_공연을_조회한다() {
        CursorPage<ShowLikeRow, Long> firstPage = showLikeReadRepository.findLikedShows(memberId, null, 1);
        CursorPage<ShowLikeRow, Long> secondPage =
                showLikeReadRepository.findLikedShows(memberId, firstPage.nextPosition(), 1);

        assertThat(firstPage.items()).extracting(ShowLikeRow::showId).containsExactly(showId3);
        assertThat(secondPage.items()).extracting(ShowLikeRow::showId).containsExactly(showId2);
    }

    @Test
    void 찜한_공연이_없으면_빈_슬라이스를_반환한다() {
        CursorPage<ShowLikeRow, Long> result = showLikeReadRepository.findLikedShows(-1L, null, 10);

        assertThat(result.items()).isEmpty();
        assertThat(result.hasNext()).isFalse();
        assertThat(result.nextPosition()).isNull();
    }
}
