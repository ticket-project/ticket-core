package com.ticket;

import org.springframework.boot.SpringApplication;
import org.springframework.modulith.Modulith;

/**
 * Ticket Core 실행 진입점이다.
 *
 * <p>이 모듈이 composition root로서 core-api/core-app/core-infra를 한 프로세스로 조립한다.
 * API와 background worker를 함께 실행하며, worker는 {@code worker.enabled}로 끌 수 있다.
 *
 * <p>{@code sharedModules}에는 <b>업무 의미가 없고 거의 모든 module이 참조하는 leaf 계약
 * module</b>만 넣는다 — {@code shared}(호출 대상 계약), {@code error}(오류 계약과 전역 handler),
 * {@code web}(REST 응답 봉투). 이렇게 두면 각 module의 {@code allowedDependencies}는 업무 module
 * 의존만 담아 읽기 쉬워지고, 세 module을 향한 실제 edge는
 * {@code com.ticket.ModularityTests.APPROVED_DEPENDENCY_DAG}가 module별로 고정한다.
 *
 * <p>{@code sharedModules}는 이 세 module을 모든 {@code @ApplicationModuleTest}에도 포함시킨다.
 * 그래서 이 자리에 bean을 등록하는 코드를 두면 모든 module의 STANDALONE 테스트가 그 배선을 함께
 * 띄운다 — {@code shared}에 {@code @Configuration}을 두지 않는 이유이고
 * ({@code com.ticket.shared.SharedModulePurityTest}가 강제한다), {@code web}에도 두지 않는다.
 */
@Modulith(sharedModules = {"shared", "error", "web"})
public class TicketApplication {

    public static void main(String[] args) {
        SpringApplication.run(TicketApplication.class, args);
    }
}
