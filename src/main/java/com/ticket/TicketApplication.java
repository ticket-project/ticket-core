package com.ticket;

import org.springframework.boot.SpringApplication;
import org.springframework.modulith.Modulith;

/**
 * Ticket Core 실행 진입점이다.
 *
 * <p>이 모듈이 composition root로서 core-api/core-app/core-infra를 한 프로세스로 조립한다.
 * API와 background worker를 함께 실행하며, worker는 {@code worker.enabled}로 끌 수 있다.
 *
 * <p>{@code com.ticket.shared}는 지금은 공개 계약이 없는 빈 package다(오류 처리는
 * {@code com.ticket.core.support.exception}의 전역 구조로 되돌렸다). 그래도
 * {@code package-info.java}에 {@code @ApplicationModule}을 선언해 두어 Spring Modulith가
 * {@code shared}를 7번째 module로 인식하고, {@code sharedModules}가 그 이름을 참조할 수 있다.
 */
@Modulith(sharedModules = "shared")
public class TicketApplication {

    public static void main(String[] args) {
        SpringApplication.run(TicketApplication.class, args);
    }
}
