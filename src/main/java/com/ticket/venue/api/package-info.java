/**
 * venue가 다른 module에 공개하는 계약이다.
 *
 * <p>행위 계약 둘: {@link com.ticket.venue.api.VenueLookupApi}(공연장 존재 확인·표시값·지역 조회)와
 * {@link com.ticket.venue.api.VenueSeatLookupApi}(좌석 주소·배치 좌표 조회). 나머지 넷은 그 계약이 돌려주는 값이다.
 *
 * <p>{@link com.ticket.venue.api.Region}(공연장 소재 지역)은 이 package의 유일한 enum이다 — "공개 계약에는 interface + record만" 원칙에서 벗어나지만,
 * show가 검색 조건·표시값으로 함께 쓰는 공용 어휘라 복제하면 원본이 둘로 갈린다. show는 이 enum을 그대로 참조한다.
 */
@NullMarked
@org.springframework.modulith.NamedInterface("api")
package com.ticket.venue.api;

import org.jspecify.annotations.NullMarked;
