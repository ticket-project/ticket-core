/**
 * Show BC: Show, Category, Genre, ShowGenre, Performer, Performance, Grade, PerformanceGrade와
 * 공연·회차 조회를 소유한다. 찜(ShowLike)의 HTTP endpoint·use case도 여기 있지만, 찜의 데이터·
 * 불변식은 favorite module이 소유한다. 물리 공연장(Venue)·물리 좌석(Seat)은 venue module이
 * 소유하고, Show는 venueId scalar column만 갖는다.
 *
 * 공개 계약:
 * - PerformanceSaleCatalog
 * - PerformanceVenueLayoutCatalog (booking이 좌석 판매 편성·seat-map 조합에 쓰는 snapshot)
 */
@org.springframework.modulith.ApplicationModule(displayName = "Show", allowedDependencies = {"venue", "favorite", "member"})
package com.ticket.show;
