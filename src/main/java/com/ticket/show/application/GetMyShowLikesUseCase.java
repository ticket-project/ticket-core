package com.ticket.show.application;

import com.ticket.show.application.ShowSummaryBatchReadRepository;
import com.ticket.show.application.ShowSummaryRow;
import com.ticket.show.application.ShowLikeSummaryView;
import com.ticket.error.InvalidRequestException;
import com.ticket.like.LikeEntry;
import com.ticket.like.LikeQuery;
import com.ticket.like.LikeType;
import com.ticket.member.MemberLookup;
import com.ticket.shared.CursorPage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 내 찜 목록을 조회한다. like가 찜 항목(likeId·targetId·likedAt)을 커서 페이지로 주면,
 * 그 targetId 집합으로 show 자기 데이터를 다시 조회해 표시값(제목·이미지·공연장 이름)을 조립한다 —
 * like는 show 표시값을 모른다.
 *
 * <p>표시값을 찾지 못한 showId(삭제된 공연)는 목록에서 조용히 건너뛴다. {@code hasNext}/
 * {@code nextPosition}은 like가 준 페이지 값을 그대로 쓰므로, 건너뛴 항목이 있으면 이번
 * 페이지의 실제 항목 수가 요청한 size보다 적을 수 있다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GetMyShowLikesUseCase {

    private static final int MAX_SIZE = 100;

    private final MemberLookup memberLookup;
    private final LikeQuery likeQuery;
    private final ShowSummaryBatchReadRepository showSummaryBatchReadRepository;

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

    public record Output(List<ShowLikeSummaryView> items, boolean hasNext, Long nextPosition) {
    }

    public Output execute(final Input input) {
        memberLookup.requireActive(input.memberId());

        final CursorPage<LikeEntry, Long> page =
                likeQuery.findLiked(LikeType.SHOW, input.memberId(), input.cursorLikeId(), input.size());

        if (page.items().isEmpty()) {
            return new Output(List.of(), page.hasNext(), page.nextPosition());
        }

        final Set<Long> showIds = page.items().stream()
                .map(LikeEntry::targetId)
                .collect(Collectors.toSet());
        final Map<Long, ShowSummaryRow> summaries = showSummaryBatchReadRepository.findSummaries(showIds);

        final List<ShowLikeSummaryView> items = page.items().stream()
                .map(entry -> toSummaryView(entry, summaries.get(entry.targetId())))
                .filter(java.util.Objects::nonNull)
                .toList();

        return new Output(items, page.hasNext(), page.nextPosition());
    }

    private ShowLikeSummaryView toSummaryView(final LikeEntry entry, final ShowSummaryRow summary) {
        if (summary == null) {
            return null;
        }
        return new ShowLikeSummaryView(
                summary.showId(),
                summary.title(),
                summary.image(),
                summary.startDate(),
                summary.endDate(),
                summary.venueName(),
                entry.likedAt()
        );
    }
}
