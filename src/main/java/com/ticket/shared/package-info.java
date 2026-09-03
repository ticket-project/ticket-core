/**
 * 모든 Application Module에 공유될 수 있는 최소 계약을 위한 자리다.
 *
 * <p>오류 처리는 {@code com.ticket.core.support.exception}의 전역 {@code ErrorType}/
 * {@code CoreException} 구조로 되돌렸다. 여기에 두는 class는 특정 Entity나 기술 adapter가
 * 아니고 업무 vocabulary를 전혀 담지 않는, 둘 이상의 독립 module이 의미 그대로 공유하는 순수
 * 범용 유틸리티여야 한다. 업무 code나 메시지, Repository, HTTP DTO, Redis key 등 기술·업무에
 * 결합된 타입은 두지 않는다.
 *
 * <p>다른 module이 직접 호출해야 하는 이 module의 공개 API이므로 다른 module의 공개 계약과
 * 같은 자리(module root)에 둔다 — {@code internal}에 두면 닫힌 module 캡슐화 때문에 다른
 * module이 참조할 수 없다({@code com.ticket.ModularityTests}가 이를 실측으로 확인한다).
 * 현재 {@link RequiredInput}({@code UseCase.Input} 필수 component 판정)과
 * {@link CursorPage}(커서 페이징 조회 결과)가 있다.
 *
 * <p><b>{@code @ApplicationModule}을 선언하는 이유</b>: package-info에 annotation이 없으면
 * javac가 {@code package-info.class}를 만들지 않아(이 저장소 toolchain에서 실측 확인) Spring
 * Modulith의 {@code direct-sub-packages} 감지가 이 package를 아예 module로 보지 못한다. class가
 * 하나도 없어도 이 annotation만으로 {@code shared}가 7번째 module로 잡히고,
 * {@code TicketApplication}의 {@code @Modulith(sharedModules = "shared")}가 이름으로 참조할
 * 대상이 실제로 존재하게 된다.
 */
@ApplicationModule(displayName = "Shared")
package com.ticket.shared;

import org.springframework.modulith.ApplicationModule;
