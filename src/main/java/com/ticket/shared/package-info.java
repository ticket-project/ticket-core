/**
 * 모든 Application Module에 공유될 수 있는 최소 계약을 위한 자리다.
 *
 * <p>오류 처리는 {@code com.ticket.core.support.exception}의 전역 {@code ErrorType}/
 * {@code CoreException} 구조로 되돌렸으므로 아직 이 package에는 class가 없다. 여기에 타입을
 * 추가할 때는 둘 이상의 독립 module에서 의미가 같고, 특정 Entity나 기술 adapter가 아니며, 모든
 * Application Module 테스트에 항상 포함돼도 되는 것이어야 한다. 업무 code나 메시지, Repository,
 * HTTP DTO, Redis key 등 기술·업무에 결합된 타입은 두지 않는다.
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
