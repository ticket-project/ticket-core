package com.ticket.catalog.internal.domain.showlike.repository;

import com.ticket.catalog.internal.domain.showlike.model.ShowLike;

import java.util.Optional;

/**
 * 찜 aggregate의 저장과 복원을 담당하는 도메인 Repository다.
 *
 * <p>{@link #like(Long, Long)}은 scalar id만 받는다 — 호출자(catalog의 use case)가
 * identity internal entity(Member)를 참조하지 않고 memberId/showId만 넘길 수 있도록,
 * entity를 실제로 구성하는 일은 이 인터페이스의 구현({@code ShowLikeRepositoryAdapter})에 맡긴다.
 */
public interface ShowLikeRepository {

    ShowLike like(Long memberId, Long showId);

    void delete(ShowLike showLike);

    boolean existsByMemberIdAndShowId(Long memberId, Long showId);

    Optional<ShowLike> findByMemberIdAndShowId(Long memberId, Long showId);

    long countByShowId(Long showId);
}
