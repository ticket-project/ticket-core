package com.ticket.like.infrastructure;

import com.ticket.like.application.port.LikeQueryPort;

import com.ticket.like.LikeType;
import com.ticket.like.application.port.LikeQueryPort;
import com.ticket.like.application.LikeRow;
import com.ticket.core.infra.support.InfraReadRepositoryTestSupport;
import com.ticket.member.account.domain.Member;
import com.ticket.shared.CursorPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@Import(QuerydslLikeQueryPort.class)
@SuppressWarnings("NonAsciiCharacters")
class QuerydslLikeQueryPortTest extends InfraReadRepositoryTestSupport {

    @Autowired
    private LikeQueryPort likeQueryPort;

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
        var venue = persistVenue(title + " 공연장", com.ticket.venue.Region.SEOUL);
        var show = persistShow(title, venue, null, 0L,
                java.time.LocalDateTime.now().minusDays(5), java.time.LocalDateTime.now().plusDays(5));
        persistLike(member, show);
        return show.getId();
    }

    @Test
    void 찜한_대상을_최신순으로_조회한다() {
        CursorPage<LikeRow, Long> result = likeQueryPort.findLiked(LikeType.SHOW, memberId, null, 2);

        assertThat(result.items()).extracting(LikeRow::targetId)
                .containsExactly(showId3, showId2);
        assertThat(result.nextPosition()).isNotNull();
        assertThat(result.hasNext()).isTrue();
    }

    @Test
    void 커서_이후의_찜한_대상을_조회한다() {
        CursorPage<LikeRow, Long> firstPage = likeQueryPort.findLiked(LikeType.SHOW, memberId, null, 1);
        CursorPage<LikeRow, Long> secondPage =
                likeQueryPort.findLiked(LikeType.SHOW, memberId, firstPage.nextPosition(), 1);

        assertThat(firstPage.items()).extracting(LikeRow::targetId).containsExactly(showId3);
        assertThat(secondPage.items()).extracting(LikeRow::targetId).containsExactly(showId2);
    }

    @Test
    void 찜한_대상이_없으면_빈_슬라이스를_반환한다() {
        CursorPage<LikeRow, Long> result = likeQueryPort.findLiked(LikeType.SHOW, -1L, null, 10);

        assertThat(result.items()).isEmpty();
        assertThat(result.hasNext()).isFalse();
        assertThat(result.nextPosition()).isNull();
    }
}
