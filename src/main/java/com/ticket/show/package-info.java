/**
 * Show module(옛 catalog): Show, Category, Genre, ShowGenre, Performer, Performance, Grade,
 * PerformanceGrade, 공연·회차 조회를 소유한다. {@link com.ticket.show.domain.performance.Performance}는
 * 회차 정체성과 일정(startTime/endTime)만 소유한다 — 예매 접수 기간·Hold 한도·대기열 진입 정책은
 * Booking BC의 {@code booking.domain.performancepolicy.model.PerformanceSalesPolicy}가 소유하고,
 * {@code performanceId} scalar로만 연결된다(ADR 0006 "Performance의 책임 혼재" A2). 찜(ShowLike)의
 * HTTP endpoint와 use case도 여기 있지만, 찜의 데이터·불변식은 {@code com.ticket.favorite} module이
 * 소유한다(아래 참고). 물리 공연장(Venue)과 물리 좌석(Seat)은 {@code com.ticket.venue} module이
 * 소유한다 — {@link com.ticket.show.domain.show.Show}는 {@code venueId} scalar column만 갖는다(아래
 * 참고).
 *
 * <p>구현은 하위 package(web/application/domain/infrastructure)에 있고, 이 module root에는 다른
 * module이 쓰는 공개 계약만 둔다: {@link com.ticket.show.PerformanceSaleCatalog}/
 * {@link com.ticket.show.PerformanceVenueLayoutCatalog}(booking이 좌석 판매 편성·seat-map 조합에
 * 쓰는 snapshot — Performance+Show+Venue+Seat+Grade 데이터가 섞인 façade이며, 내부에서 venue
 * module의 공개 계약을 호출해 조립한다). 예매 정책 조회 공개 계약({@code BookingPolicyLookup}/
 * {@code BookingPolicySnapshot})은 정책 소유권이 booking으로 이관되며 사라졌다 — booking은 이제
 * 자기 local DB로 정책을 조회한다.
 *
 * <p>{@code PerformanceSeat}(회차별 좌석 판매 상태)는 show가 아니라 booking 소유이며 Task 7에서
 * scalar ID 참조로 정리됐다.
 *
 * <p><b>Venue(물리 공연장)·Seat(물리 좌석)는 venue module이 소유한다.</b> 원래 이 module 안에
 * 있었지만(찜과 같은 이유로 BC 재편 대상), Show.venue(@ManyToOne)를 scalar {@code venueId}
 * column으로 바꾸고 venue module로 옮겼다. show가 목록·상세·검색 응답에 venue 표시값(이름·지역)을
 * 채울 때는 {@link com.ticket.venue.VenueLookup}을(배치 조회는
 * {@code show.application.support.VenueDisplays} 헬퍼로) 부르고, Region 검색 조건은
 * {@code VenueLookup.findIdsByRegion}로 venueId 집합을 해석해 {@code show.venueId.in(...)}으로
 * 바꾼다. dangling venueId(참조 무결성 FK가 없으므로 가능)는 "venue 없는 show"와 같은 값(null
 * 표시값)으로 통일해 처리한다.
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
 * {@code com.ticket.DomainPurityTest}가 강제한다(6개 BC 전체에 같은 규칙을 적용). 같은 원칙으로
 * show.domain은 venue module도 참조하지 않는다 — venue 표시값 조합은 항상 application 층(infra의
 * read repository)이 한다. 자세한 배경은 {@code docs/adr/0006-bounded-context-module-boundaries.md}를
 * 본다.
 *
 * <p>{@code /api/v1/shows/{showId}/venue-layout}({@link com.ticket.show.application.show.query.GetVenueLayoutUseCase})은
 * 원래 booking 소유였다 — booking 데이터를 전혀 참조하지 않는 passthrough였다. Venue BC 재편으로
 * show가 직접 {@code ShowRepository}+{@code VenueLookup}을 호출하도록 옮겨왔다. URL·응답 계약은
 * 그대로다.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Show", allowedDependencies = {"venue", "favorite", "member"})
package com.ticket.show;
