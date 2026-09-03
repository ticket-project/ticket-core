package com.ticket.core.infra.showlike;

import com.ticket.core.domain.showlike.model.ShowLike;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface SpringDataShowLikeJpaRepository extends JpaRepository<ShowLike, Long> {

    boolean existsByMember_IdAndShow_Id(Long memberId, Long showId);

    Optional<ShowLike> findByMember_IdAndShow_Id(Long memberId, Long showId);

    long countByShow_Id(Long showId);
}
