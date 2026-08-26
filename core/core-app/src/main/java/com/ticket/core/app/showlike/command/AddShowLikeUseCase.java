package com.ticket.core.app.showlike.command;

import com.ticket.support.error.CoreException;
import com.ticket.core.app.error.ApplicationErrorType;
import com.ticket.core.domain.member.model.Member;
import com.ticket.core.domain.member.query.MemberFinder;
import com.ticket.core.domain.show.model.Show;
import com.ticket.core.domain.show.query.ShowFinder;
import com.ticket.core.domain.showlike.model.ShowLike;
import com.ticket.core.domain.showlike.repository.ShowLikeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class AddShowLikeUseCase {

    private final ShowLikeRepository showLikeRepository;
    private final MemberFinder memberFinder;
    private final ShowFinder showFinder;

    public record Input(Long memberId, Long showId) {
    }

    public record Output(
            Long showId,
            boolean liked,
            long likeCount) {
    }

    public Output execute(final Input input) {
        validateInput(input);

        final Member member = memberFinder.findActiveMemberById(input.memberId());

        if (showLikeRepository.existsByMember_IdAndShow_Id(input.memberId(), input.showId())) {
            return new Output(input.showId(), true, countLikes(input.showId()));
        }

        final Show show = showFinder.findById(input.showId());

        try {
            showLikeRepository.save(new ShowLike(member, show));
        } catch (DataIntegrityViolationException e) {
            throw new CoreException(ApplicationErrorType.SHOW_LIKE_ALREADY_EXISTS,
                    "이미 찜한 공연입니다. memberId=" + input.memberId() + ", showId=" + input.showId());
        }

        return new Output(input.showId(), true, countLikes(input.showId()));
    }

    private void validateInput(final Input input) {
        if (input == null || input.memberId() == null || input.showId() == null) {
            throw new CoreException(ApplicationErrorType.INVALID_INPUT, "memberId와 showId는 필수입니다.");
        }
    }

    private long countLikes(final Long showId) {
        return showLikeRepository.countByShow_Id(showId);
    }
}
