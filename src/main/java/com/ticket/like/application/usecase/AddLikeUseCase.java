package com.ticket.like.application.usecase;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ticket.error.InvalidRequestException;
import com.ticket.like.LikeCommand;
import com.ticket.like.LikeInfo;
import com.ticket.like.LikeType;
import com.ticket.member.MemberLookup;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 대상 존재 확인은 이 use case의 책임이 아니다 — like는 다른 BC의 entity 존재 여부를 자기
 * invariant로 잡지 않는다(memberId·likeType·targetId 조합의 유일성만 보장한다). 존재하지 않는
 * targetId를 찜해도 조용히 저장된다. 회원 활성 확인은 다르다 — JWT 인증 필터는 서명·만료만
 * 검사하고 탈퇴 여부를 재확인하지 않으므로(탈퇴해도 access token은 만료 전까지 유효하다),
 * 탈퇴 회원이 자기 데이터를 건드리지 못하게 하려면 여기서 직접 {@link MemberLookup#requireActive}로
 * 다시 확인해야 한다.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class AddLikeUseCase {

    private final MemberLookup memberLookup;
    private final LikeCommand likeCommand;

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

        final LikeInfo info = likeCommand.like(input.memberId(), input.likeType(), input.targetId());
        return new Output(input.targetId(), info.liked(), info.likeCount());
    }
}
