/**
 * Admission module: admission token 설정, claim decode와 검증, 만료·위조 실패 해석, Booking에
 * 공개하는 Admission 검증 API를 소유한다.
 *
 * <p>실제 코드는 아직 이 module로 이동하지 않았다 — 이 package는 target module 경계만 먼저 선언한
 * 빈 skeleton이다.
 */
@ApplicationModule(displayName = "Admission", allowedDependencies = {})
package com.ticket.admission;

import org.springframework.modulith.ApplicationModule;
