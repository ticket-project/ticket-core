/**
 * Venue BC: 물리 공연장(Venue)과 그 안의 물리 좌석(Seat)을 소유한다. 좌석은 회차와 무관하게 존재한다(회차별 판매 상태는 booking의 PerformanceSeat).
 *
 * <p>공개 계약: - VenueLookupApi / VenueSnapshot (공연장 존재 확인·표시값·지역 조회) - VenueSeatLookupApi / VenueSeatSnapshot /
 * VenueSeatSnapshot (좌석 주소·배치 좌표 조회)
 *
 * <p>이 module은 업무 module을 하나도 참조하지 않는다. 의존은 entity가 상속하는 {@code shared :: jpa}와 not-found 계약을 상속하는 {@code shared ::
 * exception}뿐이다.
 *
 * <p>공개 계약은 {@code venue.api}({@code @NamedInterface("api")})에 있고 module root에는 {@code package-info.java}만 둔다.
 * {@link com.ticket.venue.api.Region}(공연장 소재 지역)은 그 package의 유일한 예외 타입이다 — "공개 계약에는 interface + record만" 원칙에서 벗어난
 * enum이지만, show가 검색 조건·표시값으로 함께 쓰는 공용 어휘라 여기 복제하면 원본이 둘로 갈린다. show는 이 enum을 그대로 참조한다.
 */
@NullMarked
@org.springframework.modulith.ApplicationModule(
        displayName = "Venue",
        allowedDependencies = {"shared :: jpa", "shared :: exception", "shared :: web"})
package com.ticket.venue;

import org.jspecify.annotations.NullMarked;
