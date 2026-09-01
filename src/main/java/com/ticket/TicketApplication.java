package com.ticket;

import org.springframework.boot.SpringApplication;
import org.springframework.modulith.Modulith;

/**
 * Ticket Core 실행 진입점이다.
 *
 * <p>이 모듈이 composition root로서 core-api/core-app/core-infra를 한 프로세스로 조립한다.
 * API와 background worker를 함께 실행하며, worker는 {@code worker.enabled}로 끌 수 있다.
 *
 * <p>{@code com.ticket.shared} package는 향후 공유 module 선언을 위한 자리다. 현재는 공개 계약이
 * 없어(오류 처리는 {@code com.ticket.core.support.exception}의 전역 구조로 되돌렸다) class 파일이
 * 없는 빈 package이므로 {@code sharedModules}로 선언하지 않는다. 공유할 계약이 다시 생기면
 * {@code @Modulith(sharedModules = "shared")}로 되돌린다.
 */
@Modulith
public class TicketApplication {

    public static void main(String[] args) {
        SpringApplication.run(TicketApplication.class, args);
    }
}
