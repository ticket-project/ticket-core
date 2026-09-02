package com.ticket.core.infra.showlike;

import com.ticket.catalog.internal.domain.show.Show;
import com.ticket.core.domain.showlike.model.ShowLike;
import com.ticket.core.domain.showlike.repository.ShowLikeRepository;
import com.ticket.identity.internal.domain.member.model.Member;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * {@link ShowLikeRepository}의 JPA 구현이다.
 *
 * <p>{@code ShowLike}가 아직 {@code Member}/{@code Show}에 대한 {@code @ManyToOne} 연관을 유지하는 동안
 * (이 코드가 legacy에 남은 이유는 {@code com.ticket.showlike} package-info 참고), 이 클래스가 그 연관을
 * 실제로 만드는 유일한 지점이다. 호출자(showlike module의 {@code AddShowLikeUseCase})는 scalar id만
 * 넘기고, 여기서 {@link EntityManager#getReference}로 FK 전용 참조를 만들어 불필요한 SELECT 없이
 * 연관을 채운다.
 */
@Repository
@RequiredArgsConstructor
public class ShowLikeRepositoryAdapter implements ShowLikeRepository {

    private final SpringDataShowLikeJpaRepository jpaRepository;
    private final EntityManager entityManager;

    @Override
    public ShowLike like(final Long memberId, final Long showId) {
        final Member memberRef = entityManager.getReference(Member.class, memberId);
        final Show showRef = entityManager.getReference(Show.class, showId);
        return jpaRepository.save(new ShowLike(memberRef, showRef));
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
