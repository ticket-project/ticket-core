/**
 * Catalog module: Show, Performance, Seat, 공연별 예매 가능 시간과 Hold 한도, PerformanceQueuePolicy,
 * QueueMode, QueueLevel, 공연·회차·좌석 조회를 소유한다.
 *
 * <p>구현은 모두 {@code internal} 아래에 있고, 이 module root에는 다른 module이 쓰는 공개 계약만
 * 둔다: {@link com.ticket.catalog.BookingPolicyLookup}/{@link com.ticket.catalog.BookingPolicySnapshot}
 * (booking의 즉시 판단용 예매 정책·가격 snapshot), {@link com.ticket.catalog.ShowLookup}/
 * {@link com.ticket.catalog.ShowSummary}(showlike 등이 쓰는 show 존재 확인·표시값 조회),
 * {@link com.ticket.catalog.CatalogMetadata}(metadata module이 조합하는 code/label).
 *
 * <p>{@code PerformanceSeat}(회차별 좌석 판매 상태)는 catalog가 아니라 booking 소유다(Task 7에서
 * 이동). 그때까지 legacy {@code com.ticket.core.*} 아래의 PerformanceSeat 관련 코드가 이 module의
 * {@code internal} entity(Performance, Seat, Show)를 직접 참조하는 임시 결합이 남아 있다.
 */
@ApplicationModule(displayName = "Catalog", allowedDependencies = {})
package com.ticket.catalog;

import org.springframework.modulith.ApplicationModule;
