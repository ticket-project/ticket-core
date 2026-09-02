package com.ticket.core.app.showlike.query;

import com.ticket.core.support.exception.CoreException;
import com.ticket.core.support.exception.ErrorType;
import com.ticket.identity.internal.domain.member.model.Member;
import com.ticket.identity.internal.domain.member.repository.MemberRepository;
import com.ticket.catalog.internal.domain.show.repository.ShowRepository;
import com.ticket.core.domain.showlike.repository.ShowLikeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ticket.core.app.support.validation.RequiredInput;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GetShowLikeStatusUseCase {

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
        final Member member = memberRepository.findActiveById(input.memberId())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND_DATA));
        if (!showRepository.existsById(input.showId())) {
            throw new CoreException(ErrorType.NOT_FOUND_DATA,
                    "공연을 찾을 수 없습니다. id=" + input.showId());
        }

        final boolean liked = showLikeRepository.existsByMemberIdAndShowId(member.getId(), input.showId());
        final long likeCount = showLikeRepository.countByShowId(input.showId());
        return new Output(input.showId(), liked, likeCount);
    }

}
