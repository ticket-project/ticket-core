package com.ticket;

import org.springframework.boot.SpringApplication;
import org.springframework.modulith.Modulith;

/**
 * Ticket Core 실행 진입점이다.
 *
 * <p>이 모듈이 composition root로서 core-api/core-app/core-infra를 한 프로세스로 조립한다.
 * API와 background worker를 함께 실행하며, worker는 {@code worker.enabled}로 끌 수 있다.
 *
 * <p>{@code shared}는 모든 Application Module 테스트에 포함되는 공유 module이다.
 */
@Modulith(sharedModules = "shared")
public class TicketApplication {

    public static void main(String[] args) {
        SpringApplication.run(TicketApplication.class, args);
    }
}
