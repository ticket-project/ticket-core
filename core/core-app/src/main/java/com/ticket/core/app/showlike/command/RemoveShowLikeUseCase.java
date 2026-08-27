package com.ticket.core.app.showlike.command;

import com.ticket.support.error.CoreException;
import com.ticket.core.app.error.ApplicationErrorType;
import com.ticket.core.domain.member.repository.MemberRepository;
import com.ticket.core.domain.show.repository.ShowRepository;
import com.ticket.core.domain.showlike.repository.ShowLikeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ticket.core.app.support.validation.RequiredInput;

@Service
@Transactional
@RequiredArgsConstructor
public class RemoveShowLikeUseCase {

    private final ShowLikeRepository showLikeRepository;
    private final MemberRepository memberRepository;
    private final ShowRepository showRepository;

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
        memberRepository.getActiveById(input.memberId());
        showRepository.requireExists(input.showId());

        showLikeRepository.findByMemberIdAndShowId(input.memberId(), input.showId())
                .ifPresent(showLikeRepository::delete);

        return new Output(
                input.showId(),
                false,
                showLikeRepository.countByShowId(input.showId())
        );
    }

}
