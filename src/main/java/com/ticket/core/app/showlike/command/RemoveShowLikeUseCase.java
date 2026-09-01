package com.ticket.core.app.showlike.command;

import com.ticket.core.support.exception.CoreException;
import com.ticket.core.support.exception.ErrorType;
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
        memberRepository.findActiveById(input.memberId())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND_DATA));
        if (!showRepository.existsById(input.showId())) {
            throw new CoreException(ErrorType.NOT_FOUND_DATA,
                    "공연을 찾을 수 없습니다. id=" + input.showId());
        }

        showLikeRepository.findByMemberIdAndShowId(input.memberId(), input.showId())
                .ifPresent(showLikeRepository::delete);

        return new Output(
                input.showId(),
                false,
                showLikeRepository.countByShowId(input.showId())
        );
    }

}
