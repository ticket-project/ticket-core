/**
 * Member BC. 회원 생성은 소셜 첫 로그인 때 member의 {@code MemberAccountApi.resolveSocialAccount}가 한다. 인증 흐름의 조립은 security가 소유하고
 * member는 security를 참조하지 않는다.
 */
@NullMarked
@org.springframework.modulith.ApplicationModule(
        displayName = "Member",
        allowedDependencies = {"shared :: api", "shared :: web", "shared :: exception", "shared :: jpa"})
package com.ticket.member;

import org.jspecify.annotations.NullMarked;
