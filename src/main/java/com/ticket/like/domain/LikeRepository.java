package com.ticket.like.domain;

import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.ticket.shared.api.CursorPage;

/**
 * 찜 aggregate의 저장과 복원을 담당하는 도메인 Repository다. 구현은 Spring Data JPA가 만든다.
 *
 * <p>{@link #like(Long, Long)}은 scalar id만 받는다 — member·대상 모두 이 module 밖의 값이라(모듈을 넘나드는 JPA 연관관계 금지, ADR 0003 §4)
 * {@code EntityManager.getReference}로 FK 전용 참조를 만들 필요 없이 값 그대로 entity를 구성한다.
 */
public interface LikeRepository extends Repository<Like, Long> {
    default Like like(final Long memberId, final Long targetId) {
        return save(new Like(memberId, targetId));
    }

    <S extends Like> S save(S like);

    void delete(Like like);

    boolean existsByMemberIdAndTargetId(Long memberId, Long targetId);

    Optional<Like> findByMemberIdAndTargetId(Long memberId, Long targetId);

    long countByTargetId(Long targetId);

    /** 찜 id 내림차순 커서 페이지다. {@code size + 1}건을 읽어 다음 페이지 유무를 정한다. */
    default CursorPage<Like, Long> findLiked(final Long memberId, final @Nullable Long cursorLikeId, final int size) {
        final PageRequest page = PageRequest.of(0, size + 1);
        final List<Like> rows =
                cursorLikeId == null ? findFirstPage(memberId, page) : findAfterId(memberId, cursorLikeId, page);
        if (rows.isEmpty()) {
            return CursorPage.empty();
        }
        final boolean hasNext = rows.size() > size;
        final List<Like> pageRows = hasNext ? rows.subList(0, size) : rows;
        final @Nullable Long nextPosition = hasNext ? pageRows.getLast().getId() : null;
        return new CursorPage<>(pageRows, hasNext, nextPosition);
    }

    @Query("SELECT l FROM Like l WHERE l.memberId = :memberId ORDER BY l.id DESC")
    List<Like> findFirstPage(@Param("memberId") Long memberId, Pageable pageable);

    @Query("""
            SELECT l FROM Like l
            WHERE l.memberId = :memberId AND l.id < :cursorLikeId
            ORDER BY l.id DESC
            """)
    List<Like> findAfterId(
            @Param("memberId") Long memberId, @Param("cursorLikeId") Long cursorLikeId, Pageable pageable);
}
