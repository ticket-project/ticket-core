package com.ticket.like.usecase;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ticket.like.domain.LikeRepository;
import com.ticket.like.domain.LikeType;
import com.ticket.member.api.MemberLookupApi;
import com.ticket.shared.api.InputChecks;

import lombok.RequiredArgsConstructor;

/**
 * 대상 존재 확인은 하지 않는다 — {@link AddLikeUseCase}의 설명과 같은 이유다. 회원 활성 확인만 직접 재확인한다.
 *
 * <p>찜하지 않은 상태로 불러도 예외 없이 현재 상태(liked=false)를 돌려준다(멱등).
 */
@Service
@Transactional
@RequiredArgsConstructor
public class RemoveLikeUseCase {
    private final MemberLookupApi memberLookup;
    private final LikeRepository likeRepository;

    public record Input(Long memberId, LikeType likeType, Long targetId) {
        public Input {
            InputChecks.requirePositiveId(memberId, "memberId");
            InputChecks.requireProvided(likeType, "likeType");
            InputChecks.requirePositiveId(targetId, "targetId");
        }
    }

    /** 공개 API JSON 이름 {@code showId}는 대상이 공연뿐이던 시절의 계약이라 그대로 고정한다. */
    public record Output(@JsonProperty("showId") Long targetId, boolean liked, long likeCount) {}

    public Output execute(final Input input) {
        final Long memberId = input.memberId();
        final LikeType likeType = input.likeType();
        final Long targetId = input.targetId();
        memberLookup.requireActive(memberId);

        likeRepository
                .findByMemberIdAndLikeTypeAndTargetId(memberId, likeType, targetId)
                .ifPresent(likeRepository::delete);

        return new Output(targetId, false, likeRepository.countByLikeTypeAndTargetId(likeType, targetId));
    }
}
