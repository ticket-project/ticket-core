/**
 * Web 기술 모듈: 이 앱이 <b>HTTP로 말하는 방식</b>을 소유한다. Bounded Context가 아니다.
 *
 * <p>모든 REST 응답을 감싸는 공통 봉투({@link com.ticket.web.ApiResponse}/
 * {@link com.ticket.web.ErrorMessage}/{@link com.ticket.web.ResultType})와 무한스크롤 응답
 * 형식({@link com.ticket.web.SliceResponse})이 여기 있다. {@code shared}·{@code error}가 아닌
 * 이유는 ADR 0003을 본다.
 *
 * <p><b>이 module은 아무 module도 참조하지 않는 leaf여야 한다.</b> 여기서 {@code error}의 예외나
 * 오류 code를 참조하면 곧바로 {@code error <-> web} 순환이 되어 {@code com.ticket.ModularityTests}가
 * 실패한다.
 *
 * <p><b>{@code sharedModules}에 선언한다</b>({@code TicketApplication} 참고) — HTTP를 노출하는
 * module이면 거의 다 참조하는 leaf 계약이라, 각 module의 {@code allowedDependencies}에
 * {@code "web"}을 일일이 적는 대신 전역 허용으로 둔다. 어느 module이 실제로 web을 참조하는지는
 * {@code com.ticket.ModularityTests.APPROVED_DEPENDENCY_DAG}가 module별로 고정한다.
 *
 * <p>그 대신 <b>이 module에도 bean을 등록하는 코드를 두지 않는다</b> — {@code sharedModules}는
 * 모든 {@code @ApplicationModuleTest}에 이 module을 포함시키므로, 배선이 있으면 모든 module의
 * STANDALONE 테스트가 그것을 함께 띄운다({@code com.ticket.shared}와 같은 이유다).
 *
 * <p><b>{@code allowedDependencies = {}}를 명시하는 이유</b>: 속성을 생략해도 컴파일은 되지만,
 * 그러면 위 "leaf여야 한다"는 약속이 {@code APPROVED_DEPENDENCY_DAG} 스냅샷에만 기대게 된다.
 * 빈 값을 명시해 Modulith {@code verify()} 자체가 leaf 위반을 잡게 한다.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Web", allowedDependencies = {})
package com.ticket.web;
