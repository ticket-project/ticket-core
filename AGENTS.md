# Ticket 저장소 작업 가이드

이 문서는 사람과 AI 에이전트가 공통으로 따르는 루트 작업 기준이며, **모든 도구의 단일 진입점**이다.
Codex와 Copilot은 이 파일을 직접 읽고, Claude Code는 루트 `CLAUDE.md`의 `@AGENTS.md` 임포트로 읽는다.
도구별로 다른 지침을 따로 두지 않는다. 규칙을 바꿀 때는 이 파일과 `docs/`만 고친다.

## 기본 원칙

- 설계와 관련된 질문에는 항상 여러 방안을 알려주고 각각의 장단점과 추천도 알려줘서 사용자가 선택할 수 있게끔 한다.
- 문서 작성과 기본 응답은 한국어로 작성한다.
- 파일은 UTF-8, BOM 없이 유지한다.
- 요청 목표, 제약, 완료조건을 먼저 구체화한다.
- 요청되지 않은 기능 추가, 대규모 리팩터링, 부가 추상화는 피한다.
- 변경은 작업 범위 안에서만 수행한다.
- 기존 미커밋 변경은 사용자 작업으로 보고 되돌리지 않는다.
- 파괴적 작업은 명시적으로 요청받은 경우에만 수행한다.

## 작업별로 먼저 읽을 문서

이 파일에는 공통 규칙만 둔다. 상세한 판단 기준은 아래 문서가 원본이며, **작업을 시작하기 전에 해당 문서를 연다.**

| 작업 성격 | 먼저 읽을 문서 |
| --- | --- |
| 도메인 개념을 이름으로 부를 때(이슈 제목, 테스트 이름, 제안) | `CONTEXT.md` |
| Selection·Hold, 주문 생명주기처럼 "왜 이렇게 했는지"가 걸리는 변경 | `docs/adr/` |
| 새 코드의 모듈·패키지 위치, 경계 위반 진단 | `/place-code` 스킬 |
| 모듈 책임, 패키지 구조, 저장소·동시성 구조 | `docs/architecture.md` |
| 기능·API·도메인 규칙 구현, Redis·분산락 작업 규칙 | `docs/development.md` |
| 주문·hold 생성·취소·만료와 outbox 후처리 | `docs/core-booking-lifecycle.md` |
| 무엇을 검증할지 고르기, 새 테스트 추가 | `docs/testing.md` |
| 로컬 실행, 프로파일, Flyway, 배포, 관측 지표 | `docs/operations.md` |
| 부하 테스트 실행과 용량 판정 | `/loadtest` 스킬, 진입점은 `docs/load-test.md` |
| 커밋·브랜치·PR | `docs/development.md` 의 커밋과 PR 절차·컨벤션 |

전체 맥락은 `README.md`, 실제 경계는 `settings.gradle`과 각 모듈 `build.gradle`, 강제되는 규칙은
관련 테스트 코드가 최종 기준이다.

`CONTEXT.md`는 도메인 용어집이다. 출력에서 도메인 개념을 부를 때 여기 정의된 말을 쓰고,
`_Avoid_`에 적힌 동의어로 흘러가지 않는다. 필요한 개념이 용어집에 없으면 그 자체가 신호다.
`docs/adr/`의 결정과 어긋나는 제안을 할 때는 조용히 덮지 않고 어긋난다는 사실을 먼저 밝힌다.

`docs/archive/`는 완료·폐기된 설계 기록이다. **명시적으로 요청받지 않는 한 읽지 않고**, 검색
결과에 걸리더라도 현재 구조의 근거로 인용하지 않는다. 아직 반영되지 않은 설계는
`docs/superpowers/`에 있다.

부하 테스트의 실제 시나리오와 실행 옵션은 형제 저장소 `../gatling-test/README.md`와
`../gatling-test/console/README.md`를 기준으로 본다.

## 저장소를 읽는 순서

1. `settings.gradle`
   - 멀티 모듈 경계를 확인한다.
2. 루트 `build.gradle`과 각 모듈 `build.gradle`
   - 의존 방향과 실행 모듈을 확인한다.
3. `docs/development.md`, `docs/architecture.md`
   - 현재 구현 상태와 모듈 경계를 확인한다.
4. `core/core-api`
   - HTTP/WebSocket 진입점, 보안, 설정을 본다.
5. `core/core-app`
   - use case, 트랜잭션 경계, 조회 포트를 본다.
6. `core/core-domain`
   - 엔티티, 도메인 정책, repository/Redis port를 본다.
7. `core/core-infra`
   - Querydsl 조회 구현, Redis, WebSocket, 외부 HTTP, AOP 구현체를 본다.
8. 관련 테스트
   - ArchUnit과 도메인 테스트로 실제 강제 규칙을 확인한다.

## 계층 경계

의존 방향은 `core-api` → `core-app` → `core-domain`이고, `core-infra`는 어댑터로서 `core-app`과
`core-domain`을 향한다. 반대 방향은 `CoreLayerArchitectureTest`가 막는다.

새 코드를 어디에 둘지 판단하는 절차와 자주 틀리는 지점은 **`/place-code` 스킬**이 원본이다.
각 모듈이 무엇을 담는지와 27개 책임별 위치는 `docs/architecture.md`를 본다.

### 절대 금지 (ArchUnit이 실패시킨다)

- `core-domain`이 `data`·`stereotype` 외의 Spring을 참조하는 것.
  `@Transactional`, `ApplicationEventPublisher`, Querydsl, SpEL, HTTP, 보안 전부 막는다.
- `core-app`이 Querydsl·web·http·security·messaging·scheduling을 참조하는 것.
- `core-api`가 `core-domain`이나 `core-infra`를 프로덕션 코드에서 참조하는 것.
  계약 테스트만 `testImplementation`으로 도메인 픽스처를 쓴다.
- 실행 모듈이 도메인 port를 직접 부르는 것. use case를 거친다.
- 도메인 타입이 API 경계로 새는 것. 요청 DTO는 문자열로 받고 변환은 `core-app`이 한다.


## 핵심 흐름

```text
HTTP/WebSocket 요청
  -> core/core-api controller/config/security
  -> core/core-app command/query use case
  -> core/core-domain 정책·엔티티 + port(repository/store/publisher/client)
  -> core/core-infra adapter(Querydsl/Redis/WebSocket/HTTP)
  -> core/core-api response 또는 WebSocket message
```

## 도메인별 주의 지점

- `auth`
  - JWT, refresh token, OAuth2, security filter chain, 공개 API 노출을 확인한다.
- `hold`, `order`
  - 주문 시작/취소/만료와 hold 해제 일관성, 부분 상태 전이를 확인한다.
- `performanceseat`
  - selection/hold 충돌, 좌석 상태 계산, 실시간 브로드캐스트를 확인한다.
- `queue`
  - queue token, TTL, 만료 처리, admitted/waiting 상태 전이를 확인한다.
- Redis / Redisson
  - key naming, TTL, expiration listener, scheduler, 락 범위를 확인한다.

## 검증 명령

작업 성격에 맞게 **가장 좁은 검증부터** 실행한다. 무엇을 돌릴지 고르는 표, 구조 테스트 명령,
통합 테스트 조건, 결과 보고 규칙은 **`/verify` 스킬**이 원본이다.

- Redis adapter, key, TTL, expiration listener를 바꿨으면 `:core:core-infra:integrationTest`까지
  실행한다(Docker 필요).
- 구조나 모듈 경계를 건드렸으면 ArchUnit 구조 테스트를 먼저 돌린다.
- 문서만 바꿨으면 Java 빌드 대신 `rg`와 `git diff --check`를 쓴다.
- CI와 같은 전체 검증은 `./gradlew test :core:core-infra:integrationTest :core:core-api:bootJar`다.

각 테스트가 무엇을 고정하는지와 새 테스트를 쓰는 관례는 `docs/testing.md`를 본다.

## 코드 리뷰

리뷰 요청을 받았을 때는 아래 기준을 적용한다. 사람과 리뷰 봇 모두 같은 기준을 따른다.

- 리뷰, 요약, 코멘트, 제안은 항상 한국어로 작성한다.
- 패치만 보지 말고 주변 코드, 호출 흐름, 관련 설정, 관련 테스트까지 함께 읽는다.
- 취향성 스타일 지적보다 실제 결함 가능성, 회귀 위험, 테스트 공백을 우선한다.
- `core-api`, `core-app`, `core-domain`, `core-infra` 경계 위반 여부를 먼저 확인한다(`docs/architecture.md`).
- 보안, 동시성, 트랜잭션 경계, Redis TTL과 만료 처리, 테스트 공백을 점검한다.
- `@Transactional` 경계, 읽기 전용 조회, 예외 처리, null 반환, JPA fetch 전략은 Java 변경에서 항상 본다.
- findings first 원칙을 따르고 심각도 높은 순서로 적는다. 각 이슈는 왜 문제인지, 어떤 조건에서
  깨지는지, 어디를 봐야 하는지를 짧게 적는다.
- 근거가 약한 코멘트는 남기지 않는다. 치명적 문제가 없으면 그 사실을 명시하고 남은 검증 공백만 덧붙인다.
- 요청 범위를 벗어난 기능 추가, 리팩터링, 추상화 제안은 최소화한다.

## 작업 보고

마무리 보고에는 아래를 포함한다.

1. 변경 파일
2. 핵심 변경점
3. 검증 결과
4. 남은 리스크 또는 후속 선택지

## 커밋 및 PR

- **커밋은 사용자가 명시적으로 요청할 때만 만든다.**
- **작업 브랜치에서 작업하고 `master`에 직접 커밋하거나 push하지 않는다.** `master` push는 곧 운영 배포다.
- 커밋 전 변경 범위에 맞는 검증을 실행하고 결과를 확인한다.
- 하나의 커밋에는 하나의 목적만 담고, 기존 사용자 변경과 섞지 않는다.
- 반영은 `gh pr merge --squash` 또는 `--rebase`를 쓴다. 선형 이력을 유지하므로 merge commit을 만들지 않는다.
- 이미 원격에 올라간 커밋 이력을 변경하려면 먼저 사용자 승인을 받는다.

형식은 Conventional Commits 기반 `<type>(<scope>): <한국어 설명>`이다. 허용 type 목록, scope 선택
기준, 9단계 절차와 충돌 검증은 **`/commit-pr` 스킬**이 원본이다. 요약은
[커밋과 PR](docs/development.md#커밋과-pr)에 있다.

## Agent skills

엔지니어링 스킬(`/triage`, `/to-tickets`, `/to-spec`, `/wayfinder`, `/domain-modeling` 등)이
이 저장소에서 쓸 설정이다.

### 이슈 트래커

이슈는 `ticket-project/ticket-core`의 GitHub Issues에 두고 `gh` CLI로 다룬다.
`docs/agents/issue-tracker.md`를 본다.

### 트리아지 라벨

표준 다섯 개(`needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`)를
기본 이름 그대로 쓴다. `docs/agents/triage-labels.md`를 본다.

### 도메인 문서

단일 컨텍스트다. 루트 `CONTEXT.md`와 `docs/adr/`를 쓴다. `docs/agents/domain.md`를 본다.
