/**
 * Venue BC: 물리 공연장(Venue)과 그 안의 물리 좌석(Seat)을 소유한다. 좌석은 회차와 무관하게 존재한다(회차별 판매 상태는 booking의 PerformanceSeat).
 *
 * <p>공개 계약: - VenueLookupApi / VenueSnapshot (공연장 존재 확인·표시값·지역 조회) - VenueSeatLookupApi / VenueSeatSnapshot /
 * VenueSeatSnapshot (좌석 주소·배치 좌표 조회)
 *
 * <p>이 module은 업무 module을 하나도 참조하지 않는다. 의존은 entity가 상속하는 {@code shared :: jpa}와 not-found 계약을 상속하는 {@code shared ::
 * exception}뿐이다.
 *
 * <p>공개 계약은 {@code venue.api}({@code @NamedInterface("api")})에 있고 module root에는 {@code package-info.java}만 둔다. 공개면은
 * interface와 record뿐이다 — 지역({@link com.ticket.venue.domain.Region})은 Venue entity의 필드이자 venue가 값 집합을 소유한 도메인 enum이라
 * {@code venue.domain}에 있고, 밖으로는 {@code VenueSnapshot.RegionView}(코드·표시명 쌍)와 {@code findIdsByRegion}이 받는 코드 문자열로만 오간다.
 * 코드 판정도 venue가 한다.
 */
@NullMarked
@org.springframework.modulith.ApplicationModule(
        displayName = "Venue",
        allowedDependencies = {"shared :: jpa", "shared :: exception", "shared :: web"})
package com.ticket.venue;

import org.jspecify.annotations.NullMarked;
