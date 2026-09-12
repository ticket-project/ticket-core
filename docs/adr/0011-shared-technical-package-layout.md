# ADR 0011: 공통 기술 코드를 shared 하위 패키지로 통합한다

## 상태

채택됨 (2026-09-10)

2026-09-13 후속 변경: `AuthenticatedMember`가 `java.security.Principal`을 구현하면 Spring MVC의
기본 Principal argument resolver가 애플리케이션 resolver보다 먼저 선택되어 실제 controller 요청이
500이 된다. shared가 member를 참조하지 않는 방향은 유지하되, 감사자 식별 계약을
`shared.AuditorPrincipal`로 좁혔다(ADR 0012).

## 결정

최상위 기술 모듈 `config`, `web`, `error`를 없애고 `shared` 하나로 통합한다.

- `shared.web`: `ApiResponse`, `SliceResponse` 등 공통 HTTP 표현
- `shared.exception`: `TicketException`, `ErrorCode`, 공통 예외와 전역 예외 처리기
- `shared.config`: Redis, Querydsl, 시간, 스케줄, 이벤트 publication, API 문서, JPA 감사 설정

업무 모듈 안의 `support` 패키지는 만들지 않는다. 모듈 공통 예외는 `<module>.exception`, 공통
도메인 타입은 `<module>.domain`, 공통 실행 계약은 `<module>.application`, 공통 기술 구현은
`<module>.infrastructure`에 둔다.

`shared`는 유일한 `sharedModules` Application Module이다. `shared.web`과 `shared.exception`은
named interface로 공개하고, `shared.config`는 내부 실행 코드로 둔다.

JPA 감사는 `shared.config.JpaAuditingConfig`가 활성화한다. 감사자 ID는 `SecurityContext` 주체가
구현한 `shared.AuditorPrincipal`에서 읽으므로 shared가 member 모듈을 참조하지 않는다.
`AuthenticatedMember`는 이 계약으로 회원 ID를 제공한다.

## 이유

업무 모듈을 먼저 정하고 그 어느 곳에도 속하지 않는 기술 코드만 마지막에 공통화한다. `config`,
`web`, `error`라는 기술 분류를 독립 Application Module로 두면 업무 모듈 의존 그래프가 불필요하게
세 갈래로 늘어난다. 공통 코드를 한 모듈에 모으되, 공개 계약과 실행 배선을 하위 패키지로 구분한다.

## 결과

업무 모듈은 공통 표현과 예외 계약을 `shared :: *`로 참조한다. shared 설정은 모든 단독 모듈
기동 테스트에 포함되므로, 설정은 해당 환경에서도 필요한 빈을 제공해야 한다. 이 규칙은
`ModularityTests`와 `SharedModulePurityTest`가 강제한다.

ADR 0003의 최상위 `config`·`web`·`error` module 배치와 ADR 0002·0010의 이전 패키지 경로는 이
결정으로 대체한다. 오류 코드, HTTP 상태, JSON 응답 형식은 바꾸지 않는다.
