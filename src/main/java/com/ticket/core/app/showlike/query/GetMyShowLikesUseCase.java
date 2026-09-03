package com.ticket.core.app.showlike.query;

import com.ticket.core.support.exception.ErrorType;
import com.ticket.shared.CursorPage;
import com.ticket.core.app.showlike.query.model.ShowLikeSummaryView;
import com.ticket.identity.internal.domain.member.model.Member;
import com.ticket.identity.internal.domain.member.repository.MemberRepository;
import com.ticket.core.support.exception.CoreException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import com.ticket.shared.RequiredInput;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GetMyShowLikesUseCase {

    private static final int MAX_SIZE = 100;

    private final MemberRepository memberRepository;
    private final ShowLikeReadRepository showLikeReadRepository;

    /**
     * @param cursorLikeId 이전 페이지 마지막 찜 id. 첫 페이지면 null이다.
     */
    public record Input(Long memberId, Long cursorLikeId, int size) {
        public Input {
            RequiredInput.positiveId(memberId, "memberId");
            RequiredInput.sizeWithin(size, MAX_SIZE, "size");
        }
    }

    public record Output(List<ShowLikeSummaryView> items, boolean hasNext, Long nextPosition) {
    }

    public Output execute(final Input input) {
        final Member member = memberRepository.findActiveById(input.memberId())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND_DATA));
        final CursorPage<ShowLikeSummaryView, Long> page =
                showLikeReadRepository.findMyLikedShows(member.getId(), input.cursorLikeId(), input.size());
        return new Output(page.items(), page.hasNext(), page.nextPosition());
    }

}
