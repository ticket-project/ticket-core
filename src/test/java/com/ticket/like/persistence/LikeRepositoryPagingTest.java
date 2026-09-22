package com.ticket.like.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import com.ticket.like.domain.Like;
import com.ticket.like.domain.LikeRepository;
import com.ticket.like.domain.LikeType;
import com.ticket.shared.api.CursorPage;
import com.ticket.testsupport.persistence.InfraReadRepositoryTestSupport;

/**
 * 찜 페이징과 중복 방지의 실제 DB 동작을 고정한다.
 *
 * <p>fixture는 Like만 만든다 — LIKES에는 FK가 없고(모듈을 넘나드는 FK 제거, like V1·V2) like는 대상 존재도 확인하지 않으므로, member·venue·show entity를
 * 만들어 봐야 이 테스트가 보는 것에 아무 영향이 없다.
 */
@Import(LikeRepositoryAdapter.class)
@SuppressWarnings("NonAsciiCharacters")
class LikeRepositoryPagingTest extends InfraReadRepositoryTestSupport {
    private static final long MEMBER_ID = 1L;
    private static final long TARGET_1 = 101L;
    private static final long TARGET_2 = 102L;
    private static final long TARGET_3 = 103L;

    @Autowired
    private LikeRepository likeRepository;

    @BeforeEach
    void 찜_세_건을_저장한다() {
        entityManager.persist(new Like(MEMBER_ID, LikeType.SHOW, TARGET_1));
        entityManager.persist(new Like(MEMBER_ID, LikeType.SHOW, TARGET_2));
        entityManager.persist(new Like(MEMBER_ID, LikeType.SHOW, TARGET_3));
        flushAndClear();
    }

    @Test
    void 찜한_대상을_최신순으로_조회한다() {
        CursorPage<Like, Long> result = likeRepository.findLiked(LikeType.SHOW, MEMBER_ID, null, 2);

        assertThat(result.items()).extracting(Like::getTargetId).containsExactly(TARGET_3, TARGET_2);
        assertThat(result.nextPosition()).isNotNull();
        assertThat(result.hasNext()).isTrue();
    }

    @Test
    void 커서_이후의_찜한_대상을_조회한다() {
        CursorPage<Like, Long> firstPage = likeRepository.findLiked(LikeType.SHOW, MEMBER_ID, null, 1);
        CursorPage<Like, Long> secondPage =
                likeRepository.findLiked(LikeType.SHOW, MEMBER_ID, firstPage.nextPosition(), 1);

        assertThat(firstPage.items()).extracting(Like::getTargetId).containsExactly(TARGET_3);
        assertThat(secondPage.items()).extracting(Like::getTargetId).containsExactly(TARGET_2);
    }

    @Test
    void 찜한_대상이_없으면_빈_슬라이스를_반환한다() {
        CursorPage<Like, Long> result = likeRepository.findLiked(LikeType.SHOW, -1L, null, 10);

        assertThat(result.items()).isEmpty();
        assertThat(result.hasNext()).isFalse();
        assertThat(result.nextPosition()).isNull();
    }

    /**
     * {@code AddLikeUseCase}가 {@code DataIntegrityViolationException}을 중복 찜으로 바꾸는 근거다 — LIKES가 실제로 이 예외를 내는 경우는
     * {@code UK_LIKES_MEMBER_TARGET} 위반뿐이다(FK 없음, NOT NULL 세 컬럼은 {@code Like} 생성자가 먼저 막는다).
     */
    @Test
    void 같은_회원이_같은_대상을_두_번_찜하면_무결성_위반이다() {
        assertThatThrownBy(() -> {
                    likeRepository.like(MEMBER_ID, LikeType.SHOW, TARGET_1);
                    flushAndClear();
                })
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
