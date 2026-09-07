package com.ticket.show.application.showlike.query;

import com.ticket.show.domain.show.repository.ShowRepository;
import com.ticket.error.InvalidRequestException;
import com.ticket.error.NotFoundException;
import com.ticket.favorite.ShowLikeInfo;
import com.ticket.favorite.ShowLikeQuery;
import com.ticket.member.MemberLookup;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GetShowLikeStatusUseCase {

    private final MemberLookup memberLookup;
    private final ShowRepository showRepository;
    private final ShowLikeQuery showLikeQuery;

    public record Input(Long memberId, Long showId) {
        public Input {
            if (memberId == null) {
                throw new InvalidRequestException("memberId는 필수입니다.");
            }
            if (memberId <= 0) {
                throw new InvalidRequestException("memberId는 양수여야 합니다.");
            }
            if (showId == null) {
                throw new InvalidRequestException("showId는 필수입니다.");
            }
            if (showId <= 0) {
                throw new InvalidRequestException("showId는 양수여야 합니다.");
            }
        }
    }

    public record Output(Long showId,
                         boolean liked,
                         long likeCount) {
    }

    public Output execute(final Input input) {
        memberLookup.requireActive(input.memberId());

        if (!showRepository.existsById(input.showId())) {
            throw new NotFoundException("공연을 찾을 수 없습니다. id=" + input.showId());
        }

        final ShowLikeInfo info = showLikeQuery.getShowLike(input.showId(), input.memberId());
        return new Output(input.showId(), info.liked(), info.likeCount());
    }
}
