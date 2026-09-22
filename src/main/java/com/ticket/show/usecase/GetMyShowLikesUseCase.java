package com.ticket.show.usecase;

import static com.ticket.shared.api.InputChecks.requirePositiveId;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticket.like.api.LikeQueryApi;
import com.ticket.like.api.LikeSnapshot;
import com.ticket.like.api.LikeType;
import com.ticket.member.api.MemberLookupApi;
import com.ticket.shared.api.CursorPage;
import com.ticket.shared.exception.InvalidRequestException;
import com.ticket.show.domain.show.Show;
import com.ticket.show.domain.show.ShowRepository;
import com.ticket.venue.api.VenueLookupApi;

import lombok.RequiredArgsConstructor;

/**
 * 내 찜 목록을 조회한다. like가 찜 항목(likeId·targetId·likedAt)을 커서 페이지로 주면, 그 targetId 집합으로 show 자기 데이터를 다시 조회해 표시값(제목·이미지·공연장
 * 이름)을 조립한다 — like는 show 표시값을 모른다.
 *
 * <p>표시값을 찾지 못한 showId(삭제된 공연)는 목록에서 조용히 건너뛴다. {@code hasNext}/ {@code nextPosition}은 like가 준 페이지 값을 그대로 쓰므로, 건너뛴 항목이
 * 있으면 이번 페이지의 실제 항목 수가 요청한 size보다 적을 수 있다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GetMyShowLikesUseCase {
    private static final int MAX_SIZE = 100;
    private final MemberLookupApi memberLookup;
    private final LikeQueryApi likeQuery;
    private final ShowRepository showRepository;
    private final VenueLookupApi venueLookup;

    /** @param cursorLikeId 이전 페이지 마지막 찜 id. 첫 페이지면 null이다. */
    public record Input(Long memberId, @Nullable Long cursorLikeId, int size) {
        public Input {
            memberId = requirePositiveId(memberId, "memberId");
            if (size <= 0 || size > MAX_SIZE) {
                throw new InvalidRequestException("size는 1 이상 " + MAX_SIZE + " 이하여야 합니다.");
            }
        }
    }

    public record Output(
            List<Item> items, boolean hasNext, @Nullable Long nextPosition) {}

    public record Item(
            Long showId,
            @Nullable String title,
            @Nullable String image,
            @Nullable LocalDate startDate,
            @Nullable LocalDate endDate,
            @Nullable String venue,
            LocalDateTime likedAt) {}

    public Output execute(final Input input) {
        memberLookup.requireActive(input.memberId());

        final CursorPage<LikeSnapshot, Long> page =
                likeQuery.findLiked(LikeType.SHOW, input.memberId(), input.cursorLikeId(), input.size());

        if (page.items().isEmpty()) {
            return new Output(List.of(), page.hasNext(), page.nextPosition());
        }

        final Set<Long> showIds =
                page.items().stream().map(LikeSnapshot::targetId).collect(Collectors.toSet());
        final Map<Long, Show> shows = showRepository.findSummaries(showIds);
        final VenueDisplays venues = VenueDisplays.load(
                venueLookup, shows.values().stream().map(Show::getVenueId).toList());

        final List<Item> items = page.items().stream()
                .map(entry -> toItem(entry, shows.get(entry.targetId()), venues))
                .filter(Objects::nonNull)
                .toList();

        return new Output(items, page.hasNext(), page.nextPosition());
    }

    /** 찜 목록의 이미지는 원본 경로 그대로다 — 목록 카드용 변환({@code ShowCardImagePathConverter})을 쓰지 않는 기존 계약이다. */
    private @Nullable Item toItem(final LikeSnapshot entry, final @Nullable Show show, final VenueDisplays venues) {
        if (show == null) {
            return null;
        }
        return new Item(
                show.getId(),
                show.getTitle(),
                show.getImage(),
                show.getStartDate(),
                show.getEndDate(),
                venues.nameOf(show.getVenueId()),
                entry.likedAt());
    }
}
