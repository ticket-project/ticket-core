/**
 * 다른 module이 <b>호출하는</b> 공유 계약만 두는 자리다.
 *
 * <p>여기에 두는 class는 특정 Entity나 기술 adapter가 아니고 업무 vocabulary를 전혀 담지 않는,
 * 둘 이상의 독립 module이 의미 그대로 공유하는 타입이어야 한다.
 *
 * <p><b>bean을 등록하는 코드는 두지 않는다.</b> {@code TicketApplication}이
 * {@code @Modulith(sharedModules = "shared")}를 선언하므로 이 module은 모든
 * {@code @ApplicationModuleTest}에 포함된다 — bean 등록이 여기 있으면 모든 module의 STANDALONE
 * 테스트가 그 배선을 함께 띄운다. 전역 {@code @Configuration}은 전부 {@code com.ticket.config}가
 * 소유하고, 이 규칙은 {@code com.ticket.shared.SharedModulePurityTest}가 강제한다.
 * {@link CorsProperties}가 예외처럼 보이지만 아니다 — {@code @ConfigurationProperties} 값 홀더는
 * 스스로 bean을 등록하지 않고 주입받아 읽는 값 타입이며, 등록은 그 값을 쓰는 module이 한다.
 *
 * <p><b>이 module은 아무 module도 참조하지 않는 leaf여야 한다.</b>
 *
 * <p>REST 응답 봉투({@code ApiResponse}/{@code ErrorMessage}/{@code ResultType}/
 * {@code SliceResponse})는 여기 없다 — {@code com.ticket.web}이 소유한다(그 package-info 참고).
 *
 * <p>다른 module이 직접 호출해야 하는 공개 API이므로 module root에 둔다. 현재 {@link CursorPage}
 * (커서 페이징 조회 결과), {@link CorsProperties}, {@link UuidSupplier}가 있다.
 *
 * <p><b>{@code @ApplicationModule}을 선언하는 이유</b>: package-info에 annotation이 없으면
 * javac가 {@code package-info.class}를 만들지 않아 Spring Modulith의 {@code direct-sub-packages}
 * 감지가 이 package를 아예 module로 보지 못한다.
 *
 * <p><b>{@code allowedDependencies = {}}를 명시하는 이유</b>: 이 module은 {@code sharedModules}로
 * 선언돼 있어 속성을 아예 생략해도 컴파일된다. 하지만 생략하면 Modulith {@code verify()}가 이
 * module의 나가는 의존에 아무 제약도 걸지 않는다 — "leaf여야 한다"는 위 문장이 실행 검증 없이
 * {@code com.ticket.ModularityTests.APPROVED_DEPENDENCY_DAG} 스냅샷에만 기대게 된다. 빈 값을
 * 명시하면 leaf 위반이 스냅샷이 아니라 {@code verify()} 자체에서 잡힌다.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Shared", allowedDependencies = {})
package com.ticket.shared;

