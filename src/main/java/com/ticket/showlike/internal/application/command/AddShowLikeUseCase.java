package com.ticket.showlike.internal.application.command;

import com.ticket.catalog.ShowLookup;
import com.ticket.shared.RequiredInput;
import com.ticket.core.domain.showlike.repository.ShowLikeRepository;
import com.ticket.core.support.exception.CoreException;
import com.ticket.core.support.exception.ErrorType;
import com.ticket.identity.MemberLookup;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class AddShowLikeUseCase {

    private final ShowLikeRepository showLikeRepository;
    private final MemberLookup memberLookup;
    private final ShowLookup showLookup;

    public record Input(Long memberId, Long showId) {
        public Input {
            RequiredInput.positiveId(memberId, "memberId");
            RequiredInput.positiveId(showId, "showId");
        }
    }

    public record Output(
            Long showId,
            boolean liked,
            long likeCount) {
    }

    public Output execute(final Input input) {
        memberLookup.requireActive(input.memberId());

        if (showLikeRepository.existsByMemberIdAndShowId(input.memberId(), input.showId())) {
            return new Output(input.showId(), true, countLikes(input.showId()));
        }

        showLookup.requireExisting(input.showId());

        try {
            showLikeRepository.like(input.memberId(), input.showId());
        } catch (DataIntegrityViolationException e) {
            throw new CoreException(ErrorType.SHOW_LIKE_ALREADY_EXISTS,
                    "이미 찜한 공연입니다. memberId=" + input.memberId() + ", showId=" + input.showId());
        }

        return new Output(input.showId(), true, countLikes(input.showId()));
    }


    private long countLikes(final Long showId) {
        return showLikeRepository.countByShowId(showId);
    }
}
