/**
 * ShowLike module: 회원의 Show 좋아요 추가·삭제·조회, {@code (memberId, showId)} 중복 방지를 소유한다.
 *
 * <p>실제 코드는 아직 이 module로 이동하지 않았다 — 이 package는 target module 경계만 먼저 선언한
 * 빈 skeleton이다.
 */
@ApplicationModule(displayName = "ShowLike", allowedDependencies = {"catalog", "identity"})
package com.ticket.showlike;

import org.springframework.modulith.ApplicationModule;
