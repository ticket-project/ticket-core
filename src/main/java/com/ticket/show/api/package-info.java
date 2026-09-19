/**
 * show가 다른 module에 공개하는 계약이다.
 *
 * <p>행위 계약 둘: {@link com.ticket.show.api.PerformanceSaleCatalogApi}(booking이 판매 편성·주문 snapshot에 쓰는
 * 회차 표시값), {@link com.ticket.show.api.PerformanceVenueLayoutCatalogApi}(정적 seat-map 조합에 쓰는 배치·좌표·등급
 * 표시값과 공연 단위 진입점이 쓰는 대표 회차 조회). 각각이 돌려주는 snapshot ({@link
 * com.ticket.show.api.PerformanceSaleSnapshot}, {@link
 * com.ticket.show.api.PerformanceVenueLayout})은 데이터라 접미사를 붙이지 않는다.
 */
@NullMarked
@org.springframework.modulith.NamedInterface("api")
package com.ticket.show.api;

import org.jspecify.annotations.NullMarked;
