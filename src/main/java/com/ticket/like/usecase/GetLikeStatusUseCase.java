package com.ticket.like.usecase;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ticket.like.api.LikeCountSnapshot;
import com.ticket.like.api.LikeQueryApi;
import com.ticket.like.api.LikeType;
import com.ticket.member.api.MemberLookupApi;
import com.ticket.shared.api.InputChecks;

import lombok.RequiredArgsConstructor;

/** 대상 존재 확인은 하지 않는다 — {@link AddLikeUseCase}의 설명과 같은 이유다. 회원 활성 확인만 직접 재확인한다. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GetLikeStatusUseCase {
    private final MemberLookupApi memberLookup;
    private final LikeQueryApi likeQuery;

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
        memberLookup.requireActive(input.memberId());

        final LikeCountSnapshot info = likeQuery.get(input.likeType(), input.targetId(), input.memberId());
        return new Output(input.targetId(), info.liked(), info.likeCount());
    }
}
