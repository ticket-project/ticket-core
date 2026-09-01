package com.ticket.core.infra.showlike;

import com.ticket.core.domain.showlike.model.ShowLike;
import com.ticket.core.domain.showlike.repository.ShowLikeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * {@link ShowLikeRepository}의 JPA 구현이다.
 */
@Repository
@RequiredArgsConstructor
public class ShowLikeRepositoryAdapter implements ShowLikeRepository {

    private final SpringDataShowLikeJpaRepository jpaRepository;

    @Override
    public ShowLike save(final ShowLike showLike) {
        return jpaRepository.save(showLike);
    }

    @Override
    public void delete(final ShowLike showLike) {
        jpaRepository.delete(showLike);
    }

    @Override
    public boolean existsByMemberIdAndShowId(final Long memberId, final Long showId) {
        return jpaRepository.existsByMember_IdAndShow_Id(memberId, showId);
    }

    @Override
    public Optional<ShowLike> findByMemberIdAndShowId(final Long memberId, final Long showId) {
        return jpaRepository.findByMember_IdAndShow_Id(memberId, showId);
    }

    @Override
    public long countByShowId(final Long showId) {
        return jpaRepository.countByShow_Id(showId);
    }
}
