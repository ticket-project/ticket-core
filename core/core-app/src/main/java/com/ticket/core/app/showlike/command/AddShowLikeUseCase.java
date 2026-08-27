package com.ticket.core.app.showlike.command;

import com.ticket.support.error.CoreException;
import com.ticket.core.app.error.ApplicationErrorType;
import com.ticket.core.domain.member.model.Member;
import com.ticket.core.domain.member.repository.MemberRepository;
import com.ticket.core.domain.show.model.Show;
import com.ticket.core.domain.show.repository.ShowRepository;
import com.ticket.core.domain.showlike.model.ShowLike;
import com.ticket.core.domain.showlike.repository.ShowLikeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ticket.core.app.support.validation.RequiredInput;

@Service
@Transactional
@RequiredArgsConstructor
public class AddShowLikeUseCase {

    private final ShowLikeRepository showLikeRepository;
    private final MemberRepository memberRepository;
    private final ShowRepository showRepository;

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

        final Member member = memberRepository.getActiveById(input.memberId());

        if (showLikeRepository.existsByMemberIdAndShowId(input.memberId(), input.showId())) {
            return new Output(input.showId(), true, countLikes(input.showId()));
        }

        final Show show = showRepository.getById(input.showId());

        try {
            showLikeRepository.save(new ShowLike(member, show));
        } catch (DataIntegrityViolationException e) {
            throw new CoreException(ApplicationErrorType.SHOW_LIKE_ALREADY_EXISTS,
                    "이미 찜한 공연입니다. memberId=" + input.memberId() + ", showId=" + input.showId());
        }

        return new Output(input.showId(), true, countLikes(input.showId()));
    }


    private long countLikes(final Long showId) {
        return showLikeRepository.countByShowId(showId);
    }
}
