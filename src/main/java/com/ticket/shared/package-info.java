/**
 * 모든 Application Module에 공유되는 최소 계약이다.
 *
 * <p>{@code shared}는 {@code TicketApplication}의 {@code @Modulith(sharedModules = "shared")}로
 * 공유 module로 선언되므로 여기서는 {@code @ApplicationModule}을 붙이지 않는다. 업무 code나 메시지,
 * Repository, HTTP DTO, Redis key 등 기술·업무에 결합된 타입은 두지 않는다. 새 타입을 추가하려면
 * 둘 이상의 독립 module에서 의미가 같고, 특정 Entity나 기술 adapter가 아니며, 모든 Application
 * Module 테스트에 항상 포함돼도 되어야 한다.
 */
package com.ticket.shared;
