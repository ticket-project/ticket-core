package com.ticket.showlike.internal.application.command;

import com.ticket.catalog.ShowLookup;
import com.ticket.core.app.support.validation.RequiredInput;
import com.ticket.core.domain.showlike.repository.ShowLikeRepository;
import com.ticket.identity.MemberLookup;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class RemoveShowLikeUseCase {

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

        showLikeRepository.findByMemberIdAndShowId(input.memberId(), input.showId())
                .ifPresent(showLikeRepository::delete);

        return new Output(
                input.showId(),
                false,
                showLikeRepository.countByShowId(input.showId())
        );
    }

}
