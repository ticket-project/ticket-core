package com.ticket.core.app.showlike.query;

import com.ticket.support.error.CoreException;
import com.ticket.core.app.error.ApplicationErrorType;
import com.ticket.core.domain.member.model.Member;
import com.ticket.core.domain.member.repository.MemberRepository;
import com.ticket.core.app.showlike.query.ShowLikeReadRepository;
import com.ticket.core.app.support.cursor.CursorSlice;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GetMyShowLikesUseCase {

    private static final int MAX_SIZE = 100;

    private final MemberRepository memberRepository;
    private final ShowLikeReadRepository showLikeReadRepository;

    public record Input(Long memberId, String cursor, int size) {
    }

    public record ShowLikeSummary(
            Long showId,
            String title,
            String image,
            LocalDate startDate,
            LocalDate endDate,
            String venue,
            LocalDateTime likedAt
    ) {
    }

    public record Output(Slice<ShowLikeSummary> shows, String nextCursor) {
    }

    public Output execute(final Input input) {
        validateInput(input);
        final Member member = memberRepository.getActiveById(input.memberId());
        final Long cursorLikeId = parseCursor(input.cursor());
        final CursorSlice<ShowLikeSummary> result =
                showLikeReadRepository.findMyLikedShows(member.getId(), cursorLikeId, input.size());
        return new Output(result.slice(), result.nextCursor());
    }

    private Long parseCursor(final String cursor) {
        if (!StringUtils.hasText(cursor)) {
            return null;
        }

        try {
            return Long.parseLong(cursor);
        } catch (final NumberFormatException exception) {
            throw new CoreException(ApplicationErrorType.INVALID_INPUT, "cursor 형식이 올바르지 않습니다.");
        }
    }

    private void validateInput(final Input input) {
        if (input == null || input.memberId() == null) {
            throw new CoreException(ApplicationErrorType.INVALID_INPUT, "memberId는 필수입니다.");
        }

        if (input.size() <= 0 || input.size() > MAX_SIZE) {
            throw new CoreException(ApplicationErrorType.INVALID_INPUT, "size는 1 이상 " + MAX_SIZE + " 이하여야 합니다.");
        }
    }
}
