/**
 * Catalog module: Show, Performance, Seat, 공연별 예매 가능 시간과 Hold 한도, PerformanceQueuePolicy,
 * QueueMode, QueueLevel, 공연·회차·좌석 조회를 소유한다.
 *
 * <p>실제 코드는 아직 이 module로 이동하지 않았다 — 이 package는 target module 경계만 먼저 선언한
 * 빈 skeleton이다.
 */
@ApplicationModule(displayName = "Catalog", allowedDependencies = {})
package com.ticket.catalog;

import org.springframework.modulith.ApplicationModule;
