/**
 * 인증·인가와 그 조립을 소유하는 기술 모듈이다. 업무가 아니라 인증 기술이라 계층 대신
 * 기능({@code auth}/{@code jwt}/{@code oauth}/{@code token}/{@code http})으로 나눈다.
 */
@NullMarked
@org.springframework.modulith.ApplicationModule(
        displayName = "Security",
        allowedDependencies = {
            "member :: api",
            "shared :: api",
            "shared :: config",
            "shared :: web",
            "shared :: exception"
        })
package com.ticket.security;

import org.jspecify.annotations.NullMarked;
