package com.ticket.core.app.showlike.query;

import com.ticket.support.error.CoreException;
import com.ticket.core.app.error.ApplicationErrorType;
import com.ticket.core.domain.member.model.Member;
import com.ticket.core.domain.member.query.MemberFinder;
import com.ticket.core.domain.show.query.ShowFinder;
import com.ticket.core.domain.showlike.repository.ShowLikeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GetShowLikeStatusUseCase {

    private final ShowLikeRepository showLikeRepository;
    private final MemberFinder memberFinder;
    private final ShowFinder showFinder;

    public record Input(Long memberId, Long showId) {
    }

    public record Output(Long showId,
                         boolean liked,
                         long likeCount) {
    }

    public Output execute(final Input input) {
        validateInput(input);
        final Member member = memberFinder.findActiveMemberById(input.memberId());
        showFinder.validateShowExists(input.showId());

        final boolean liked = showLikeRepository.existsByMember_IdAndShow_Id(member.getId(), input.showId());
        final long likeCount = showLikeRepository.countByShow_Id(input.showId());
        return new Output(input.showId(), liked, likeCount);
    }

    private void validateInput(final Input input) {
        if (input == null || input.memberId() == null || input.showId() == null) {
            throw new CoreException(ApplicationErrorType.INVALID_INPUT, "memberId와 showId는 필수입니다.");
        }
    }
}
