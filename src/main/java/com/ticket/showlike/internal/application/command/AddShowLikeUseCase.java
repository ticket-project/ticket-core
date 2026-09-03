package com.ticket.showlike.internal.application.command;

import com.ticket.catalog.ShowLookup;
import com.ticket.core.domain.showlike.repository.ShowLikeRepository;
import com.ticket.showlike.internal.exception.ShowLikeAlreadyExistsException;
import com.ticket.error.InvalidRequestException;
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
            throw new ShowLikeAlreadyExistsException(input.memberId(), input.showId());
        }

        return new Output(input.showId(), true, countLikes(input.showId()));
    }


    private long countLikes(final Long showId) {
        return showLikeRepository.countByShowId(showId);
    }
}
