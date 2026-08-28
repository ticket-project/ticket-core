---
name: place-code
description: >
  ticket 저장소에서 새 코드를 어느 모듈에 둘지 판단하고, 계층 경계 위반을 진단한다.
  새 클래스·포트·use case를 만들 때, 코드를 옮길 때, ArchUnit 구조 테스트가 실패했을 때 쓴다.
paths: core/**, storage/**, support/**
allowed-tools: Bash(rg:*) Bash(./gradlew:*)
---

# 새 코드를 어디에 둘까

의존 방향은 `core-api` → `core-app` → `core-domain`이고, `core-infra`는 어댑터로서 `core-app`과
`core-domain`을 향한다. 반대 방향은 `CoreLayerArchitectureTest`가 막는다.

## 1. 이 코드가 무엇을 쓰는가

| 쓰는 것 | 두는 곳 |
| --- | --- |
| HTTP 요청·응답, 쿠키, security 설정, WebSocket 진입 | `core-api` |
| use case, 트랜잭션 경계, 여러 서비스 조립, 조회 포트와 결과 view | `core-app` |
| 엔티티, 값 객체, 도메인 정책, `*Finder`, port 선언 | `core-domain` |
| Querydsl, Redis, JWT, 암호화, 외부 HTTP, scheduler, AOP | `core-infra` |

## 2. 갈리면 이 둘을 본다

- **port는 그것을 쓰는 쪽에 둔다.** use case가 쓰면 `core-app`, `*Finder`가 쓰면 `core-domain`.
  구현은 어느 쪽이든 `core-infra`다.
- **엔티티를 다루면 도메인, 순서를 정하면 애플리케이션이다.** 규칙 판단은 `core-domain`,
  그 규칙들을 순서대로 부르는 조립은 `core-app`이다.

그래도 갈리면 **"이 코드가 사라졌을 때 무엇이 먼저 깨지는가"**를 보고 아래 표에서 찾는다.

## 3. 책임별 위치

| 책임 | 위치 |
| --- | --- |
| HTTP endpoint, 요청 검증, 인증 주체 추출, 응답 포맷 | `core-api` |
| Swagger 문서 인터페이스 | `core-api` 의 `controller.docs` |
| OAuth2 설정, security filter chain, 인증 필터 | `core-api` 의 `config.security` |
| JWT 발급·검증 구현 | `core-infra` 의 `auth.token` |
| 인증 흐름의 포트와 인증 주체·토큰 값 | `core-app` 의 `auth` |
| 회원 역할·권한 같은 업무 개념 | `core-domain` 의 `auth` 또는 `member` |
| HTTP 헤더 이름 같은 API 계약 상수 | `core-api` 의 `api` |
| 상태를 바꾸는 use case | `core-app` 의 `<기능>.command` |
| 조회 use case | `core-app` 의 `<기능>.query` |
| 트랜잭션 경계 조립, 여러 도메인 서비스 오케스트레이션 | `core-app` |
| 조회 결과 view와 검색 param | `core-app` 의 `<기능>.query.model` |
| 읽기 전용 조회 port (`*ReadRepository`) | `core-app` 의 `<기능>.query` |
| 엔티티, 값 객체 | `core-domain` 의 `<기능>.model` |
| 도메인 정책과 검증기 | `core-domain` |
| Aggregate Repository 인터페이스(순수 계약) | `core-domain` 의 `<기능>.repository` |
| Repository 어댑터와 Spring Data 인터페이스 | `core-infra` |
| 저장 기술에 중립적인 업무 상태 저장 계약 | 필요 주체에 따라 `core-domain`의 `<기능>.store` 또는 `core-app`의 포트 |
| 분산락 port (`LockManager`, `LockKey`, `LockOptions`) | `core-app` 의 `lock` |
| 분산락 구현과 Redis key 형식 | `core-infra` 의 `lock` |
| 후속 처리 이벤트 port (`IntegrationEventPublisher`) | `core-app` 의 `event` |
| outbox 엔티티·상태·relay | `core-infra` 의 `order.outbox` |
| HTTP 커서 문자열 인코딩·디코딩 | `core-api` 의 `api.support.cursor` |
| 커서 위치 타입과 조회 결과 | `core-app` 의 `<기능>.query.model`, `support.cursor` |
| Querydsl 조회 구현과 조건·정렬·커서 헬퍼 | `core-infra` 의 `<기능>.query` |
| Redis adapter, expiration listener, WebSocket publisher, 외부 HTTP client | `core-infra` |
| 암호화, 대기열 입장 토큰 검증 같은 포트 구현 | `core-infra` |
| 시드 러너 | `core-infra` 의 `seed` |
| `@TransactionalEventListener`, background executor 설정 | `core-infra` |
| `@Scheduled` 트리거와 실행 주기 설정 | `bootstrap` 의 `worker` |
| Spring Boot main과 프로파일 설정 | `bootstrap` |
| Querydsl, P6Spy 같은 기술 설정 | `core-infra` 의 `config` |
| 프레임워크 중립 오류 계약과 예외 전달 기반 | `support:error` |
| 요청 파라미터 Bean Validation 제약 | `core-api` 의 `controller.docs` 인터페이스 |
| `UseCase.Input` 필수 component 계약 | `core-app` 의 `<기능>` UseCase record와 `support.validation` |
| 도메인 규칙이 판단하는 오류 | `core-domain` 의 `error` |
| 유스케이스가 판단하는 오류 | `core-app` 의 `error` |
| 오류 응답 형식과 Spring 예외 변환 | `core-api` 의 `api.error` |

## 4. 자주 틀리는 지점

- **주기 실행이 필요한 규칙.** 규칙은 `core-app`의 use case에, `@Scheduled` 트리거만 `core-infra`에.
  도메인에 애노테이션을 붙이는 순간 ArchUnit이 막는다.
- **Redis 상태를 읽는 조회 로직.** 좌석 상태는 DB와 Redis 점유를 합쳐 계산한다. 합치는 규칙은
  `core-app`의 query use case가 소유하고 Redis 조회 자체는 `store` port를 통한다.
- **Querydsl 조건 생성기.** `ShowConditionFactory`처럼 Querydsl 타입을 다루면 DB 연동 코드이므로
  `core-infra`에 둔다. use case가 조건을 조립하지 않는다.
- **도메인 타입이 API에 새는 경우.** 요청 DTO는 문자열로 받고 변환은 `core-app` 경계에서 한다.
  enum은 `ShowSearchCriteria.of(...)`, 값 객체는 use case `Input.of(...)`가 맡는다.
  포트 시그니처도 엔티티가 아니라 식별 값을 받는다.
- **포트를 실행 모듈이 직접 부르는 경우.** `OAuth2AuthCodeStore` 같은 도메인 포트는 컨트롤러나
  security 핸들러가 직접 호출하지 않고 use case가 감싼다.
- **설정값을 두 곳에서 읽는 경우.** 토큰 만료처럼 한 값이 저장소 TTL과 응답에 함께 쓰이면
  발급한 쪽이 결과에 담아 알려준다. 각자 설정을 읽으면 어긋난다.
- **도메인이 이벤트를 발행하거나 트랜잭션을 여는 경우.** `ApplicationEventPublisher`와
  `@Transactional`은 흐름을 엮는 방법이다. 규칙은 `core-domain`에, 경계와 발행은 `core-app`에.
- **한 기능의 짝이 다른 층에 있는 경우.** outbox 생성과 해제처럼 같은 일을 하는 코드가 갈려 있으면
  둘 중 하나가 잘못 놓인 것이다. 이름이 달라도 하는 일로 판단한다.
- **다른 도메인이 필요한 경우.** 상대 도메인의 `repository`나 `store`를 직접 부르지 않고 공개
  use case를 호출한다.
- **대기열.** 대기열 런타임은 형제 저장소 `../ticket-queue`가 소유한다. Core는 회차별 `entryType`
  계산과 admission token 검증만 담당하며 queue token 저장소나 만료 핸들러를 두지 않는다.
- **Core Redis의 용도.** seat selection, seat hold, refresh token, OAuth2 one-time auth code뿐이다.
  대기열 상태를 Core Redis에 넣지 않는다.

## 5. 구조 테스트가 실패했을 때

| 실패한 테스트 | 먼저 볼 것 |
| --- | --- |
| `CoreLayerArchitectureTest` | 계층 의존 방향. 새 import가 화살표를 거슬러 올라갔는지 |
| `CoreDomainArchitectureTest` | `core-domain`의 Spring 사용. `data`·`stereotype` 외를 썼는지 |
| `CoreDomainModuleStructureTest` | 도메인 파일 배치. **IDE 단독 실행 실패는 무시** — 상대 경로로 읽으므로 Gradle로 다시 확인한다 |
| `CoreApiArchitectureTest` | `core-api`의 의존 제약 |

**테스트를 고쳐서 통과시키지 않는다.** 규칙이 틀렸다고 판단되면 먼저
[architecture.md](../../../docs/architecture.md#아키텍처-규칙)의 근거를 읽고, 규칙을 바꿔야 한다는
사실을 사용자에게 밝힌 뒤 진행한다.

실행 명령은 `/verify`를 본다.
