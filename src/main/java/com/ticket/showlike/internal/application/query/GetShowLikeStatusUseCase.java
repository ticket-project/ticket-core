package com.ticket.showlike.internal.application.query;

import com.ticket.catalog.ShowLookup;
import com.ticket.shared.RequiredInput;
import com.ticket.core.domain.showlike.repository.ShowLikeRepository;
import com.ticket.identity.MemberLookup;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GetShowLikeStatusUseCase {

    private final ShowLikeRepository showLikeRepository;
    private final MemberLookup memberLookup;
    private final ShowLookup showLookup;

    public record Input(Long memberId, Long showId) {
        public Input {
            RequiredInput.positiveId(memberId, "memberId");
            RequiredInput.positiveId(showId, "showId");
        }
    }

    public record Output(Long showId,
                         boolean liked,
                         long likeCount) {
    }

    public Output execute(final Input input) {
        memberLookup.requireActive(input.memberId());
        showLookup.requireExisting(input.showId());

        final boolean liked = showLikeRepository.existsByMemberIdAndShowId(input.memberId(), input.showId());
        final long likeCount = showLikeRepository.countByShowId(input.showId());
        return new Output(input.showId(), liked, likeCount);
    }

}
