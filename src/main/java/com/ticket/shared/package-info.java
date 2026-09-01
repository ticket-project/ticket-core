/**
 * 모든 Application Module에 공유될 수 있는 최소 계약을 위한 자리다.
 *
 * <p>오류 처리는 {@code com.ticket.core.support.exception}의 전역 {@code ErrorType}/
 * {@code CoreException} 구조로 되돌렸으므로 현재 이 package에는 class가 없다. 그래서
 * {@code TicketApplication}도 지금은 {@code @Modulith(sharedModules = "shared")}가 아니라
 * {@code @Modulith}만 선언한다. 여기에 다시 타입을 추가할 때는 둘 이상의 독립 module에서 의미가
 * 같고, 특정 Entity나 기술 adapter가 아니며, 모든 Application Module 테스트에 항상 포함돼도
 * 되는 것이어야 한다. 그때 {@code @ApplicationModule} 없이 유지하고 {@code TicketApplication}의
 * {@code sharedModules}를 다시 {@code "shared"}로 선언한다. 업무 code나 메시지, Repository,
 * HTTP DTO, Redis key 등 기술·업무에 결합된 타입은 두지 않는다.
 */
package com.ticket.shared;
