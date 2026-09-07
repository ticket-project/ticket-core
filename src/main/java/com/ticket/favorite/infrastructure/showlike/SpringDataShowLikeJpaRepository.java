package com.ticket.favorite.infrastructure.showlike;

import com.ticket.favorite.domain.showlike.model.ShowLike;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface SpringDataShowLikeJpaRepository extends JpaRepository<ShowLike, Long> {

    boolean existsByMemberIdAndShowId(Long memberId, Long showId);

    Optional<ShowLike> findByMemberIdAndShowId(Long memberId, Long showId);

    long countByShowId(Long showId);
}
