/**
 * Like BC. 대상 존재와 요청 회원의 활성 상태를 확인하지 않는다 — 대상을 확인하려면 {@code like -> show}가 생겨 {@code show -> like}와 순환이 되고, 탈퇴 회원의 남은
 * 토큰으로 생긴 찜은 막을 가치가 작다.
 */
@NullMarked
@org.springframework.modulith.ApplicationModule(
        displayName = "Like",
        allowedDependencies = {"member :: api", "shared :: api", "shared :: web", "shared :: exception", "shared :: jpa"
        })
package com.ticket.like;

import org.jspecify.annotations.NullMarked;
