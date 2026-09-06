/**
 * 다른 module이 <b>호출하는</b> 공유 계약만 두는 자리다.
 *
 * <p>여기에 두는 class는 특정 Entity나 기술 adapter가 아니고 업무 vocabulary를 전혀 담지 않는,
 * 둘 이상의 독립 module이 의미 그대로 공유하는 타입이어야 한다. 업무 code나 메시지, Repository,
 * Redis key 등 기술·업무에 결합된 타입은 두지 않는다.
 *
 * <p><b>bean을 등록하는 코드는 두지 않는다.</b> 계약은 다른 module이 불러 쓰는 것이고
 * {@code @Configuration}은 포함하는 것만으로 적용되는 것이다. 둘이 한 module에 있으면 이 module을
 * 참조하는 쪽이 Redisson·Querydsl·Swagger 같은 기술 스택과 그 bean까지 강제로 함께 받아 "가져다
 * 쓸 수 있는 계약"이라는 성질이 무너진다. 게다가 {@code TicketApplication}이
 * {@code @Modulith(sharedModules = "shared")}를 선언하므로 이 module은 <b>모든
 * {@code @ApplicationModuleTest}에 항상 포함된다</b> — bean 등록이 여기 있으면 모든 module의
 * STANDALONE 테스트가 그 배선을 함께 띄운다. 전역 {@code @Configuration}은 전부
 * {@code com.ticket.config}가 소유하고, 이 규칙은
 * {@code com.ticket.shared.SharedModulePurityTest}가 강제한다.
 *
 * <p>{@link CorsProperties}가 예외처럼 보이지만 아니다 — {@code @ConfigurationProperties} 값
 * 홀더는 스스로 bean을 등록하지 않고 주입받아 읽는 값 타입이며, 등록은 그 값을 쓰는 module이
 * {@code @EnableConfigurationProperties}로 한다(member의 {@code SecurityConfig}).
 *
 * <p><b>이 module은 아무 module도 참조하지 않는 leaf여야 한다.</b> 여기서 다른 module의 타입을
 * 참조하면 그 module이 이미 {@code shared}를 참조하고 있는 만큼 곧바로 순환이 되어
 * {@code com.ticket.ModularityTests}가 실패한다.
 *
 * <p>REST 응답 봉투({@code ApiResponse}/{@code ErrorMessage}/{@code ResultType}/
 * {@code SliceResponse})는 여기 없다 — Jackson·Swagger에 결합된 HTTP 표현 계약이고 이 앱의 모든
 * 채널이 쓰는 것도 아니라서 {@code com.ticket.web}이 소유한다(그 package-info 참고).
 *
 * <p>다른 module이 직접 호출해야 하는 이 module의 공개 API이므로 다른 module의 공개 계약과
 * 같은 자리(module root)에 둔다 — 하위 package에 두면 닫힌 module 캡슐화 때문에 다른 module이
 * 참조할 수 없다({@code com.ticket.ModularityTests}가 이를 실측으로 확인한다).
 * 현재 {@link CursorPage}(커서 페이징 조회 결과), {@link CorsProperties}, {@link UuidSupplier}가
 * 있다.
 *
 * <p><b>아직 기준을 만족하지 못하는 것</b>: {@link CursorPage}는 실측상 {@code catalog}와 legacy
 * {@code com.ticket.core}의 showlike read 경로만 쓴다("둘 이상의 독립 module" 미달). legacy가 함께
 * 쓰는 동안 {@code catalog}로 내리면 legacy가 {@code catalog}을 참조하게 되므로
 * 옮기지 않았다 — showlike read 경로 정리가 끝나는 시점에 catalog 소유로 내린다.
 *
 * <p><b>{@code @ApplicationModule}을 선언하는 이유</b>: package-info에 annotation이 없으면
 * javac가 {@code package-info.class}를 만들지 않아(이 저장소 toolchain에서 실측 확인) Spring
 * Modulith의 {@code direct-sub-packages} 감지가 이 package를 아예 module로 보지 못한다. class가
 * 하나도 없어도 이 annotation만으로 {@code shared}가 module로 잡히고,
 * {@code TicketApplication}의 {@code @Modulith(sharedModules = ...)}가 이름으로 참조할
 * 대상이 실제로 존재하게 된다.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Shared")
package com.ticket.shared;
