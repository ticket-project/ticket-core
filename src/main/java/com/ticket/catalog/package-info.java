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
 * <p>{@code PerformanceSeat}(회차별 좌석 판매 상태)는 catalog가 아니라 booking 소유이며 Task 7에서
 * scalar ID 참조로 정리됐다. 지금 남은 임시 결합은 legacy {@code com.ticket.core.domain.showlike}
 * 뿐이다: {@code ShowLike}가 아직 이 module의 {@code internal} entity({@code Show})를 `@ManyToOne`으로
 * 직접 참조하고, {@code QuerydslShowDetailReadRepository}(catalog 소유)가 showlike의 `QShowLike`를
 * 직접 join해 `likeCount`를 채운다. showlike는 identity·catalog에 의존하지만 그 반대는 허용 dependency가
 * 아니므로(Task 9), 두 모듈 다 옮기면 순환이 생겨 Task 9에서 의도적으로 legacy에 남겨 뒀다 — showlike의
 * `package-info.java`에 같은 결합이 기록되어 있다.
 */
@ApplicationModule(displayName = "Catalog", allowedDependencies = {})
package com.ticket.catalog;

import org.springframework.modulith.ApplicationModule;
