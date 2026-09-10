/**
 * Show BC: Show, Category, Genre, ShowGenre, Performer, Performance, Grade, PerformanceGrade와
 * 공연·회차 조회를 소유한다. 물리 공연장(Venue)·물리 좌석(Seat)은 venue module이 소유하고,
 * Show는 venueId scalar column만 갖는다.
 *
 * <p>찜하기·찜 해제·찜 상태 조회의 HTTP endpoint·use case는 like module이 소유한다(공연 존재를
 * 확인하지 않아도 되는 대신 대상 표시값도 모르는 use case라 이렇게 나눴다). show에 남는 건 찜의
 * 개수를 보여주는 {@code GetShowDetailUseCase}(공연 상세)와 "내 찜 목록"을 조립하는
 * {@code GetMyShowLikesUseCase}뿐이다 — 둘 다 공연 표시값(제목·이미지 등)을 채워야 해서 show가
 * like의 공개 API를 부르는 쪽이다. 여러 BC의 "내 것"을 모으는 module(가칭 mypage)이 생기면
 * {@code GetMyShowLikesUseCase}는 그쪽으로 옮길 후보다(ADR 0008).
 *
 * 공개 계약:
 * - PerformanceSaleCatalog
 * - PerformanceVenueLayoutCatalog (booking이 좌석 판매 편성·seat-map 조합에 쓰는 snapshot)
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Show",
        allowedDependencies = {"venue", "like", "member", "shared :: *"}
)
package com.ticket.show;

