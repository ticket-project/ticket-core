package com.ticket.show.application.showlike.command;

import com.ticket.show.ShowLookup;
import com.ticket.show.domain.showlike.repository.ShowLikeRepository;
import com.ticket.error.InvalidRequestException;
import com.ticket.member.MemberLookup;
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
