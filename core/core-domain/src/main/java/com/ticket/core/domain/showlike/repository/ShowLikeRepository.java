package com.ticket.core.domain.showlike.repository;

import com.ticket.core.domain.showlike.model.ShowLike;

import java.util.Optional;

/**
 * 찜 aggregate의 저장과 복원을 담당하는 도메인 Repository다.
 */
public interface ShowLikeRepository {

    ShowLike save(ShowLike showLike);

    void delete(ShowLike showLike);

    boolean existsByMemberIdAndShowId(Long memberId, Long showId);

    Optional<ShowLike> findByMemberIdAndShowId(Long memberId, Long showId);

    long countByShowId(Long showId);
}
