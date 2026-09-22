/**
 * venue가 다른 module에 공개하는 계약이다.
 *
 * <p>행위 계약 둘: {@link com.ticket.venue.api.VenueLookupApi}(공연장 존재 확인·표시값·지역 조회)와
 * {@link com.ticket.venue.api.VenueSeatLookupApi}(좌석 주소·배치 좌표 조회). 나머지 둘({@code VenueSnapshot},
 * {@code VenueSeatSnapshot})은 그 계약이 돌려주는 값이다.
 *
 * <p>지역 enum은 여기 노출하지 않는다. {@code Region}은 Venue entity의 필드이자 venue가 값 집합을 소유한 도메인 타입이라 {@code venue.domain}에 있고, 밖으로는
 * {@code VenueSnapshot.RegionView}(코드·표시명 쌍)로 나가고 {@code findIdsByRegion}은 코드 문자열을 받아 venue가 판정한다. 호출하는 module은 지역 코드
 * 목록을 알 필요가 없다.
 */
@NullMarked
@org.springframework.modulith.NamedInterface("api")
package com.ticket.venue.api;

import org.jspecify.annotations.NullMarked;
