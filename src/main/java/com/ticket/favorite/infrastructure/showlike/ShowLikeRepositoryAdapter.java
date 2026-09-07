package com.ticket.favorite.infrastructure.showlike;

import com.ticket.favorite.domain.showlike.model.ShowLike;
import com.ticket.favorite.domain.showlike.repository.ShowLikeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * {@link ShowLikeRepository}의 JPA 구현이다.
 *
 * <p>member·show 모두 scalar id다(모듈을 넘나드는 JPA 연관관계 금지, ADR 0003 §4) — 그래서
 * {@code EntityManager.getReference}로 FK 전용 참조를 미리 만들 필요 없이 값 그대로 entity를
 * 구성한다.
 */
@Repository
@RequiredArgsConstructor
public class ShowLikeRepositoryAdapter implements ShowLikeRepository {

    private final SpringDataShowLikeJpaRepository jpaRepository;

    @Override
    public ShowLike like(final Long memberId, final Long showId) {
        return jpaRepository.save(new ShowLike(memberId, showId));
    }

    @Override
    public void delete(final ShowLike showLike) {
        jpaRepository.delete(showLike);
    }

    @Override
    public boolean existsByMemberIdAndShowId(final Long memberId, final Long showId) {
        return jpaRepository.existsByMemberIdAndShowId(memberId, showId);
    }

    @Override
    public Optional<ShowLike> findByMemberIdAndShowId(final Long memberId, final Long showId) {
        return jpaRepository.findByMemberIdAndShowId(memberId, showId);
    }

    @Override
    public long countByShowId(final Long showId) {
        return jpaRepository.countByShowId(showId);
    }
}
