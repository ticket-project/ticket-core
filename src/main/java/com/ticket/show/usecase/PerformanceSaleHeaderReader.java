package com.ticket.show.usecase;

import java.time.LocalDateTime;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import com.ticket.shared.api.CacheNames;
import com.ticket.show.api.PerformanceSaleSnapshot;
import com.ticket.show.exception.PerformanceNotFoundException;
import com.ticket.show.persistence.ShowQuerydslRepository;
import com.ticket.venue.api.VenueLookupApi;

import lombok.RequiredArgsConstructor;

/**
 * 주문 표시값 중 회차 단위 값(공연·회차, 공연장 이름, 등급)을 읽는다. 좌석 라벨은 주문마다 달라 {@link PerformanceSnapshotService}가 따로 읽는다.
 *
 * <p><b>회차별로 10분 재사용한다({@link CacheNames#PERFORMANCE_DISPLAY}).</b> 판매 중 거의 바뀌지 않는 표시값인데 예매 사용자의 주문마다 DB를 세 번
 * 읽었다(gatling-test capacity-log 2026-10-22). 공연명·공연장 이름·회차 시작 시각·등급 표시값(가격 포함)이 바뀌면 최대 10분 옛 값을 쓴다 — 그 사이 만든 주문의 표시값과
 * booking의 등급별 잔여석 조회(등급 이름·가격·순서)다. 그 사이 회차에 추가된 등급은 잔여석 응답에서 빠지고, 그 등급 좌석의 주문 생성은 실패한다. 주문 금액은 좌석 단가로 계산하므로(ADR 0005)
 * 이 값과 무관하다.
 *
 * <p><b>왜 별도 bean인가.</b> {@code @Cacheable}은 다른 bean이 부를 때만 걸린다. {@link PerformanceSnapshotService} 안의 메서드로 두면 캐시를 거치지
 * 않고 매번 DB를 읽는다.
 */
@Component
@RequiredArgsConstructor
public class PerformanceSaleHeaderReader {
    private final ShowQuerydslRepository showQuerydslRepository;
    private final VenueLookupApi venueLookupApi;

    /** 캐시에 담기는 불변 값이다. show JPA entity를 담지 않는다. */
    public record Header(
            long showId,
            @Nullable String showTitle,
            Long venueId,
            @Nullable String venueName,
            LocalDateTime performanceStartTime,
            Map<Long, PerformanceSaleSnapshot.GradeInfo> gradeInfoByPerformanceGradeId) {
        public Header {
            gradeInfoByPerformanceGradeId = Map.copyOf(gradeInfoByPerformanceGradeId);
        }
    }

    /** @throws PerformanceNotFoundException 회차가 없을 때. 공연이 없으면 {@code ShowNotFoundException}이다. 예외는 캐시되지 않는다 */
    @Cacheable(cacheNames = CacheNames.PERFORMANCE_DISPLAY, key = "#performanceId", sync = true)
    public Header read(final long performanceId) {
        final PerformanceShowRow performance = showQuerydslRepository
                .findPerformanceShow(performanceId)
                .orElseThrow(() -> new PerformanceNotFoundException(performanceId));
        final Long venueId = performance.requireVenueId();
        return new Header(
                performance.showId(),
                performance.title(),
                venueId,
                venueLookupApi.getVenueSnapshot(venueId).name(),
                performance.startTime(),
                PerformanceSnapshotService.mapGrades(
                        showQuerydslRepository.findPerformanceGrades(performanceId),
                        PerformanceSnapshotService::toGradeInfo));
    }
}
