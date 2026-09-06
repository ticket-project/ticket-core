/**
 * Web module: 이 앱이 <b>HTTP로 말하는 방식</b>을 소유한다.
 *
 * <p>모든 REST 응답을 감싸는 공통 봉투({@link com.ticket.web.ApiResponse}/
 * {@link com.ticket.web.ErrorMessage}/{@link com.ticket.web.ResultType})와 무한스크롤 응답
 * 형식({@link com.ticket.web.SliceResponse})이 여기 있다. 값은 그대로 외부 API 계약이므로
 * 모양을 바꾸면 클라이언트가 함께 깨진다 — 변경 주체가 분명해야 하는 코드라 소유 module을 둔다.
 *
 * <p><b>{@code shared}가 아닌 이유</b>: {@code com.ticket.shared}는 프로토콜·프레임워크 결합이
 * 없는 호출 대상 계약만 두는 자리다(그 package-info 참고). 봉투는 Jackson·Swagger 애노테이션을
 * 달고 있는 REST 표현 계약이고, 이 앱의 모든 채널이 쓰는 것도 아니다 — booking이 좌석 상태를
 * 발행하는 WebSocket payload는 이 봉투를 쓰지 않는다. {@code shared}에 두면 "전 module 공통
 * 유틸리티"처럼 보여 그 범위가 가려지고, 다음 사람이 같은 근거로 무엇이든 {@code shared}에
 * 넣게 된다.
 *
 * <p><b>{@code error}가 아닌 이유</b>: {@code com.ticket.error}는 오류 계약(code·예외 base·전역
 * handler)을 소유한다. 성공 응답 봉투까지 그 module에 두면 이름과 내용이 어긋난다. 방향은
 * {@code error -> web} 하나다 — handler가 오류를 HTTP 본문으로 옮길 때 이 module의 봉투를 쓴다.
 *
 * <p><b>이 module은 아무 module도 참조하지 않는 leaf여야 한다.</b> 여기서 {@code error}의 예외나
 * 오류 code를 참조하면 곧바로 {@code error <-> web} 순환이 되어 {@code com.ticket.ModularityTests}가
 * 실패한다 — {@link com.ticket.web.ApiResponse}가 오류 타입을 모른 채 완성된 code·message·data
 * 문자열만 받는 이유가 이것이다.
 *
 * <p><b>{@code sharedModules}에 선언한다</b> — {@code shared}·{@code error}와 같은 관례다
 * ({@code TicketApplication} 참고). 업무 의미가 없고 HTTP를 노출하는 module이면 거의 다 참조하는
 * leaf 계약이라, 각 module의 {@code allowedDependencies}에 {@code "web"}을 일일이 적는 대신 전역
 * 허용으로 두고 업무 module 의존만 그 목록에 남긴다. 그렇다고 참조가 감시에서 빠지지는 않는다 —
 * 어느 module이 실제로 web을 참조하는지는
 * {@code com.ticket.ModularityTests.APPROVED_DEPENDENCY_DAG}가 module별로 고정하므로, HTTP를
 * 노출하지 않던 module에 봉투가 새로 들어오면 그 테스트가 실패한다.
 *
 * <p>그 대신 <b>이 module에도 bean을 등록하는 코드를 두지 않는다</b>. {@code sharedModules}는 이
 * module을 모든 {@code @ApplicationModuleTest}에 포함시키므로, 여기 배선이 있으면 모든 module의
 * STANDALONE 테스트가 그것을 함께 띄운다({@code com.ticket.shared}와 같은 이유다).
 *
 * <p>구현이랄 것이 없어 하위 package가 없다 — 다른 module이 직접 쓰는 공개 계약뿐이므로
 * 전부 module root에 있다. 각 module의 controller가 사는 {@code <module>.web}과는
 * 다른 자리다: 그쪽은 그 module의 endpoint이고, 이 module은 그 endpoint들이 공유하는 표현
 * 계약이다.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Web")
package com.ticket.web;
