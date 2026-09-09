/**
 * Venue BC: 물리 공연장(Venue)과 그 안의 물리 좌석(Seat)을 소유한다. 좌석은 회차와 무관하게
 * 존재한다(회차별 판매 상태는 booking의 PerformanceSeat).
 *
 * 공개 계약:
 * - VenueLookup / VenueSummary (공연장 존재 확인·표시값·지역 조회)
 * - VenueSeatLookup / VenueSeatAddress / VenueSeatLayout (좌석 주소·배치 좌표 조회)
 *
 * 이 module은 업무 module을 하나도 참조하지 않는 leaf다({@code allowedDependencies = {}}).
 *
 * <p>{@link com.ticket.venue.Region}(공연장 소재 지역)은 이 module root의 유일한 예외 타입이다
 * — "root에는 interface + record만" 원칙에서 벗어난 enum이지만, show가 검색 조건·표시값으로
 * 함께 쓰는 공용 어휘라 여기 복제하면 원본이 둘로 갈린다. show는 이 enum을 그대로 참조한다.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Venue", allowedDependencies = {})
package com.ticket.venue;

