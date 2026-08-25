package com.ticket.core.domain.showlike.query;

import com.ticket.core.domain.member.model.Member;
import com.ticket.core.domain.show.model.Show;
import com.ticket.core.domain.show.meta.Region;
import com.ticket.core.domain.show.venue.Venue;
import com.ticket.core.domain.support.QueryRepositoryTestSupport;
import com.ticket.core.support.cursor.CursorSlice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@Import(ShowLikeQueryRepository.class)
@SuppressWarnings("NonAsciiCharacters")
class ShowLikeQueryRepositoryTest extends QueryRepositoryTestSupport {

    @Autowired
    private ShowLikeQueryRepository showLikeQueryRepository;

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
        CursorSlice<GetMyShowLikesUseCase.ShowLikeSummary> result = showLikeQueryRepository.findMyLikedShows(memberId, null, 2);

        assertThat(result.slice().getContent()).extracting(GetMyShowLikesUseCase.ShowLikeSummary::title)
                .containsExactly("세번째 공연", "두번째 공연");
        assertThat(result.nextCursor()).isNotBlank();
        assertThat(result.slice().hasNext()).isTrue();
    }

    @Test
    void 커서_이후의_찜한_공연을_조회한다() {
        CursorSlice<GetMyShowLikesUseCase.ShowLikeSummary> firstPage = showLikeQueryRepository.findMyLikedShows(memberId, null, 1);
        CursorSlice<GetMyShowLikesUseCase.ShowLikeSummary> secondPage =
                showLikeQueryRepository.findMyLikedShows(memberId, Long.parseLong(firstPage.nextCursor()), 1);

        assertThat(firstPage.slice().getContent()).extracting(GetMyShowLikesUseCase.ShowLikeSummary::title)
                .containsExactly("세번째 공연");
        assertThat(secondPage.slice().getContent()).extracting(GetMyShowLikesUseCase.ShowLikeSummary::title)
                .containsExactly("두번째 공연");
    }

    @Test
    void 찜한_공연이_없으면_빈_슬라이스를_반환한다() {
        CursorSlice<GetMyShowLikesUseCase.ShowLikeSummary> result = showLikeQueryRepository.findMyLikedShows(-1L, null, 10);

        assertThat(result.slice().getContent()).isEmpty();
        assertThat(result.slice().hasNext()).isFalse();
        assertThat(result.nextCursor()).isNull();
    }
}
