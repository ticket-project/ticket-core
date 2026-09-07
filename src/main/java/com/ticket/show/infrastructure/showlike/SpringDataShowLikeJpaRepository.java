package com.ticket.show.infrastructure.showlike;

import com.ticket.show.domain.showlike.model.ShowLike;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface SpringDataShowLikeJpaRepository extends JpaRepository<ShowLike, Long> {

    boolean existsByMemberIdAndShow_Id(Long memberId, Long showId);

    Optional<ShowLike> findByMemberIdAndShow_Id(Long memberId, Long showId);

    long countByShow_Id(Long showId);
}
