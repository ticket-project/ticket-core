package com.ticket.show.application.showlike.query;

import com.ticket.show.application.showlike.query.model.ShowLikeSummaryView;
import com.ticket.error.InvalidRequestException;
import com.ticket.member.MemberLookup;
import com.ticket.shared.CursorPage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GetMyShowLikesUseCase {

    private static final int MAX_SIZE = 100;

    private final MemberLookup memberLookup;
    private final ShowLikeReadRepository showLikeReadRepository;

    /**
     * @param cursorLikeId 이전 페이지 마지막 찜 id. 첫 페이지면 null이다.
     */
    public record Input(Long memberId, Long cursorLikeId, int size) {
        public Input {
            if (memberId == null) {
                throw new InvalidRequestException("memberId는 필수입니다.");
            }
            if (memberId <= 0) {
                throw new InvalidRequestException("memberId는 양수여야 합니다.");
            }
            if (size <= 0 || size > MAX_SIZE) {
                throw new InvalidRequestException("size는 1 이상 " + MAX_SIZE + " 이하여야 합니다.");
            }
        }
    }

    public record Output(java.util.List<ShowLikeSummaryView> items, boolean hasNext, Long nextPosition) {
    }

    public Output execute(final Input input) {
        memberLookup.requireActive(input.memberId());
        final CursorPage<ShowLikeSummaryView, Long> page =
                showLikeReadRepository.findMyLikedShows(input.memberId(), input.cursorLikeId(), input.size());
        return new Output(page.items(), page.hasNext(), page.nextPosition());
    }

}
