/**
 * Show module(옛 catalog): Show, Performance, Seat, 공연별 예매 가능 시간과 Hold 한도, PerformanceQueuePolicy,
 * QueueMode, QueueLevel, 공연·회차·좌석 조회를 소유한다. 찜(ShowLike)의 HTTP endpoint와 use case도
 * 여기 있지만, 찜의 데이터·불변식은 {@code com.ticket.favorite} module이 소유한다(아래 참고).
 *
 * <p>구현은 하위 package(web/application/domain/infrastructure)에 있고, 이 module root에는 다른 module이 쓰는 공개 계약만
 * 둔다: {@link com.ticket.show.BookingPolicyLookup}/{@link com.ticket.show.BookingPolicySnapshot}
 * (booking의 즉시 판단용 예매 정책·가격 snapshot), {@link com.ticket.show.PerformanceSaleCatalog}/
 * {@link com.ticket.show.PerformanceVenueLayoutCatalog}(booking이 좌석 판매 편성·seat-map 조합에
 * 쓰는 snapshot), {@link com.ticket.show.VenueLayoutLookup}(booking의
 * {@code /api/v1/shows/{showId}/venue-layout} 조회 — Venue BC가 분리되면 이 계약은 사라질
 * 임시 자리다).
 *
 * <p>{@code PerformanceSeat}(회차별 좌석 판매 상태)는 show가 아니라 booking 소유이며 Task 7에서
 * scalar ID 참조로 정리됐다.
 *
 * <p><b>찜(showlike)의 데이터는 favorite module이 소유하고, HTTP endpoint·use case는 show가
 * 소유한다.</b> 찜 개수(예: {@code Show.viewCount}와 같은 성격의 파생 지표)와 찜하기/해제하기는
 * Show를 설명하는 부가 속성이지 독자적인 업무가 아니라는 판단은 그대로다. 다만 예전에는(ADR 0003 §11)
 * 찜을 별도 module({@code com.ticket.showlike})로 뒀다가 두 module 사이 순환(show → showlike의
 * 좋아요 개수 조회, showlike → show의 공연 존재 확인·내 찜 목록 표시값 조회) 때문에 이 module 하나로
 * 흡수했었다. 이번에 찜을 다시 {@code com.ticket.favorite}로 떼어내면서, {@code show → favorite}
 * 방향(공연 상세의 찜 개수 조회, {@link com.ticket.show.application.show.query.GetShowDetailUseCase})은
 * 그대로 두고 반대 방향({@code favorite → show})만 없앴다 — favorite의 공개 계약
 * ({@code ShowLikeQuery}/{@code ShowLikeCommand})은 show 존재 확인도, 표시값 조회도 하지 않는다.
 * show의 찜 use case가 자기 {@code ShowRepository}로 공연 존재를 먼저 확인하고, 내 찜 목록의
 * 표시값도 favorite가 준 showId 집합으로 show가 직접 다시 조회해 조립한다
 * ({@link com.ticket.show.application.showlike.query.GetMyShowLikesUseCase}). 그래서 favorite는
 * 업무 module 의존이 하나도 없는 leaf가 됐다. {@code show.domain}이 favorite를 참조하지 않는지는
 * {@code com.ticket.show.domain.ShowDomainPurityTest}가 강제한다. 자세한 배경은
 * {@code docs/adr/0006-bounded-context-module-boundaries.md}를 본다.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Show", allowedDependencies = {"favorite", "member"})
package com.ticket.show;
