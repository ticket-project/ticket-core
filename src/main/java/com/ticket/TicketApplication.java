package com.ticket;

import org.springframework.boot.SpringApplication;
import org.springframework.modulith.Modulith;

/**
 * Ticket Core 실행 진입점이다.
 *
 * <p>단일 Spring Boot 애플리케이션으로 API와 background worker를 한 프로세스에서 함께 실행하며,
 * worker는 {@code worker.enabled}로 끌 수 있다.
 *
 * <p>{@code sharedModules}에는 업무 의미가 없는 공통 기술 모듈 {@code shared}만 넣는다. 이 모듈의
 * 공개 계약은 {@code shared.web}과 {@code shared.exception}이고, 실행 배선은 {@code shared.config}에 둔다.
 *
 * <p>{@code sharedModules}는 shared를 모든 {@code @ApplicationModuleTest}에 포함시킨다. 따라서
 * 공통 배선은 모듈별 기동 테스트에서도 동작해야 한다.
 */
@Modulith(sharedModules = "shared")
public class TicketApplication {

    public static void main(String[] args) {
        SpringApplication.run(TicketApplication.class, args);
    }
}
