/**
 * Identity module: Member, 이메일·소셜 로그인, OAuth2 provider adapter, 비밀번호, access/refresh
 * token, 회원 상태와 탈퇴를 소유한다.
 *
 * <p>실제 코드는 아직 이 module로 이동하지 않았다 — 이 package는 target module 경계만 먼저 선언한
 * 빈 skeleton이다.
 */
@ApplicationModule(displayName = "Identity", allowedDependencies = {})
package com.ticket.identity;

import org.springframework.modulith.ApplicationModule;
