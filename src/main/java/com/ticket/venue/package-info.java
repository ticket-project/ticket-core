/**
 * Venue module: 물리 공연장(Venue)과 그 안의 물리 좌석(Seat)을 소유한다. 좌석은 회차와 무관하게
 * 존재한다(회차별 판매 상태는 booking의 PerformanceSeat).
 *
 * <p>구현은 하위 package(domain/application/infrastructure)에 있고, 이 module root에는 다른
 * module이 쓰는 공개 계약만 둔다: {@link com.ticket.venue.VenueLookup}/
 * {@link com.ticket.venue.VenueSummary}(공연장 존재 확인·표시값·지역 조회),
 * {@link com.ticket.venue.VenueSeatLookup}/{@link com.ticket.venue.VenueSeatAddress}/
 * {@link com.ticket.venue.VenueSeatLayout}(좌석 주소·배치 좌표 조회).
 *
 * <p>이 module은 업무 module을 하나도 참조하지 않는 leaf다({@code allowedDependencies = {}}).
 * controller·오류 코드를 갖지 않는다 — venue를 등록·수정하는 API는 아직 없고, seed가 raw SQL로
 * 데이터를 적재한다.
 *
 * <p>{@link com.ticket.venue.Region}(공연장 소재 지역)은 이 module root의 유일한 예외 타입이다
 * — "root에는 interface + record만" 원칙에서 벗어난 enum이지만, show가 검색 조건·표시값으로
 * 함께 쓰는 공용 어휘라 여기 복제하면 원본이 둘로 갈린다. show는 이 enum을 그대로 참조한다.
 *
 * <p>BC(Bounded Context) 재편으로 show(옛 catalog)에서 분리됐다. 자세한 배경은
 * {@code docs/adr/0006-bounded-context-module-boundaries.md}를 본다.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Venue", allowedDependencies = {})
package com.ticket.venue;
