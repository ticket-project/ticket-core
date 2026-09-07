package com.ticket.show.infrastructure.showlike;

import com.ticket.show.domain.show.Show;
import com.ticket.show.domain.showlike.model.ShowLike;
import com.ticket.show.domain.showlike.repository.ShowLikeRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * {@link ShowLikeRepository}의 JPA 구현이다.
 *
 * <p>{@code member}는 scalar id이고 {@code show}만 이 module 소유 entity에 대한
 * {@code @ManyToOne}이라, {@link EntityManager#getReference}로 FK 전용 참조를 만들어
 * 불필요한 SELECT 없이 연관을 채우는 대상도 {@code Show}뿐이다.
 */
@Repository
@RequiredArgsConstructor
public class ShowLikeRepositoryAdapter implements ShowLikeRepository {

    private final SpringDataShowLikeJpaRepository jpaRepository;
    private final EntityManager entityManager;

    @Override
    public ShowLike like(final Long memberId, final Long showId) {
        final Show showRef = entityManager.getReference(Show.class, showId);
        return jpaRepository.save(new ShowLike(memberId, showRef));
    }

    @Override
    public void delete(final ShowLike showLike) {
        jpaRepository.delete(showLike);
    }

    @Override
    public boolean existsByMemberIdAndShowId(final Long memberId, final Long showId) {
        return jpaRepository.existsByMemberIdAndShow_Id(memberId, showId);
    }

    @Override
    public Optional<ShowLike> findByMemberIdAndShowId(final Long memberId, final Long showId) {
        return jpaRepository.findByMemberIdAndShow_Id(memberId, showId);
    }

    @Override
    public long countByShowId(final Long showId) {
        return jpaRepository.countByShow_Id(showId);
    }
}
