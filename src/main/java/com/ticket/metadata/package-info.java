/**
 * Metadata module: 여러 module이 공개한 메타데이터를 조합하는 HTTP endpoint를 소유한다.
 *
 * <p>실제 코드는 아직 이 module로 이동하지 않았다 — 이 package는 target module 경계만 먼저 선언한
 * 빈 skeleton이다.
 */
@ApplicationModule(displayName = "Metadata", allowedDependencies = {"catalog", "booking", "identity"})
package com.ticket.metadata;

import org.springframework.modulith.ApplicationModule;
