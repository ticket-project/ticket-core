package com.ticket.like.preference.application.usecase;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ticket.shared.exception.InvalidRequestException;
import com.ticket.like.LikeInfo;
import com.ticket.like.LikeQuery;
import com.ticket.like.LikeType;
import com.ticket.member.MemberLookup;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 대상 존재 확인은 하지 않는다 — {@link AddLikeUseCase}의 설명과 같은 이유다. 회원 활성 확인만
 * 직접 재확인한다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GetLikeStatusUseCase {

    private final MemberLookup memberLookup;
    private final LikeQuery likeQuery;

    public record Input(Long memberId, LikeType likeType, Long targetId) {
        public Input {
            if (memberId == null) {
                throw new InvalidRequestException("memberId는 필수입니다.");
            }
            if (memberId <= 0) {
                throw new InvalidRequestException("memberId는 양수여야 합니다.");
            }
            if (likeType == null) {
                throw new InvalidRequestException("likeType는 필수입니다.");
            }
            if (targetId == null) {
                throw new InvalidRequestException("targetId는 필수입니다.");
            }
            if (targetId <= 0) {
                throw new InvalidRequestException("targetId는 양수여야 합니다.");
            }
        }
    }

    /**
     * 공개 API JSON 이름 {@code showId}는 대상이 공연뿐이던 시절의 계약이라 그대로 고정한다.
     */
    public record Output(
            @JsonProperty("showId") Long targetId,
            boolean liked,
            long likeCount) {
    }

    public Output execute(final Input input) {
        memberLookup.requireActive(input.memberId());

        final LikeInfo info = likeQuery.get(input.likeType(), input.targetId(), input.memberId());
        return new Output(input.targetId(), info.liked(), info.likeCount());
    }
}
