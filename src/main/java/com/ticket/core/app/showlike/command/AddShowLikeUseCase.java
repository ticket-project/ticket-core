package com.ticket.core.app.showlike.command;

import com.ticket.core.support.exception.CoreException;
import com.ticket.core.support.exception.ErrorType;
import com.ticket.identity.internal.domain.member.model.Member;
import com.ticket.identity.internal.domain.member.repository.MemberRepository;
import com.ticket.catalog.internal.domain.show.Show;
import com.ticket.catalog.internal.domain.show.repository.ShowRepository;
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

        final Member member = memberRepository.findActiveById(input.memberId())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND_DATA));

        if (showLikeRepository.existsByMemberIdAndShowId(input.memberId(), input.showId())) {
            return new Output(input.showId(), true, countLikes(input.showId()));
        }

        final Show show = showRepository.findById(input.showId())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND_DATA,
                        "공연을 찾을 수 없습니다. id=" + input.showId()));

        try {
            showLikeRepository.save(new ShowLike(member, show));
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
