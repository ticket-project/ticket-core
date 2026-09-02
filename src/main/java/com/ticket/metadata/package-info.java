/**
 * Metadata module: 프론트가 한 번에 조회하는 공통 코드/Enum(카테고리, 장르, 예매 상태, 좌석/hold/주문
 * 상태, 소셜 로그인 provider, 회원 role, 판매 유형, 지역, show 정렬 기준) 조회 API를 소유한다.
 *
 * <p>자체 도메인이 없다 — catalog/booking/identity가 공개한 {@link com.ticket.catalog.CatalogMetadata},
 * {@link com.ticket.booking.BookingMetadata}, {@link com.ticket.identity.IdentityMetadata}만 주입받아
 * 결과를 조합한다. 어떤 module의 internal enum·entity·repository도 직접 import하지 않는다.
 *
 * <p>구현은 모두 {@code internal} 아래에 있고, 이 module root에는 공개 계약이 없다 — metadata를
 * 조합해 쓰는 다른 module이 없기 때문이다.
 */
@ApplicationModule(displayName = "Metadata", allowedDependencies = {"catalog", "booking", "identity"})
package com.ticket.metadata;

import org.springframework.modulith.ApplicationModule;
