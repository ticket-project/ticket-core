# Ticket 저장소 작업 가이드

사람과 AI 에이전트가 공통으로 따르는 **모든 도구의 단일 진입점**이다. Codex와 Copilot은 이 파일을
직접 읽고, Claude Code는 루트 `CLAUDE.md`의 `@AGENTS.md` 임포트로 읽는다.

**이 파일은 어디를 볼지만 정한다.** 판단 기준은 아래 표가 가리키는 문서와 스킬이 원본이고,
강제되는 규칙은 테스트와 훅이 원본이다. 규칙 본문을 여기 옮겨 적지 않는다 — 옮겨 적는 순간
원본과 어긋나고, 어긋난 쪽을 사람이 먼저 믿는다.

## 기본 원칙

- 설계 질문에는 여러 방안과 각각의 장단점, 추천을 함께 준다. 고르는 것은 사용자다.
- 문서와 응답은 한국어로 쓴다. 파일은 UTF-8, BOM 없이 유지한다.
- 요청 목표, 제약, 완료 조건을 먼저 구체화한다.
- 요청되지 않은 기능 추가, 대규모 리팩터링, 부가 추상화는 하지 않는다.
- 기존 미커밋 변경은 사용자 작업으로 보고 되돌리지 않는다. 파괴적 작업은 명시적 요청이 있을 때만 한다.
- **에이전트가 틀린 것을 발견하면 대화에서만 고치고 끝내지 않는다.**
  `docs/agents/observed-failures.md`에 적고 테스트·훅·규칙 중 하나로 옮긴다.

## 작업별로 먼저 읽을 문서

**작업을 시작하기 전에 해당 문서를 연다.**

| 작업 성격 | 먼저 읽을 문서 |
| --- | --- |
| 도메인 개념을 이름으로 부를 때(이슈 제목, 테스트 이름, 제안) | `CONTEXT.md` |
| Selection·Hold, 주문 생명주기처럼 "왜 이렇게 했는지"가 걸리는 변경 | `docs/adr/` |
| 새 코드의 모듈·패키지 위치, 경계 위반 진단, 구조 테스트 실패 | `/place-code` 스킬 |
| 모듈이 왜 그렇게 나뉘었는지, 저장소·동시성 구조 | `docs/architecture.md` |
| 기능·API·도메인 규칙 구현, Redis·분산락 작업 규칙 | `docs/development.md` |
| 요청·입력 검증을 어느 계층에 둘지, 중복 검증 판단 | `docs/validation.md` |
| 주문·hold 생성·취소·만료와 event 후속 처리 | `docs/core-booking-lifecycle.md` |
| 무엇을 검증할지 고르기, 테스트 실행, 결과 보고 | `/verify` 스킬 |
| 새 테스트를 어디에 어떻게 쓰는지 | `docs/testing.md` |
| 로컬 실행, 프로파일, Flyway, 배포, 관측 지표 | `docs/operations.md` |
| 부하 테스트 실행과 용량 판정 | `/loadtest` 스킬, 진입점은 `docs/load-test.md` |
| 커밋·브랜치·PR | `/commit-pr` 스킬 |
| 규칙이 왜 있는지, 반복해 틀리는 지점 | `docs/agents/observed-failures.md` |

전체 맥락은 `README.md`, 실제 경계는 `settings.gradle`과 각 모듈 `build.gradle`, 강제되는 규칙은
관련 테스트 코드가 최종 기준이다.

`CONTEXT.md`는 도메인 용어집이다. 출력에서 도메인 개념을 부를 때 여기 정의된 말을 쓰고
`_Avoid_`의 동의어로 흘러가지 않는다. 필요한 개념이 용어집에 없으면 그 자체가 신호다.
`docs/adr/`의 결정과 어긋나는 제안을 할 때는 조용히 덮지 않고 어긋난다는 사실을 먼저 밝힌다.

`docs/archive/`는 완료·폐기된 기록이다. **요청받지 않는 한 읽지 않고**, 검색 결과에 걸리더라도
현재 구조의 근거로 인용하지 않는다. 아직 반영되지 않은 설계는 `docs/superpowers/`에 있다.

부하 테스트의 실제 시나리오와 실행 옵션은 형제 저장소 `../gatling-test`를 기준으로 본다.

## 모듈 경계

단일 Gradle Spring Boot 프로젝트다. `com.ticket`의 직접 하위 패키지(`booking`, `catalog`,
`identity`, `admission`, `metadata`, `shared`, `web`, `config`, `error`, `seed`)가 Spring
Modulith의 닫힌 Application Module(총 10개)이고, 모듈 root에는 다른 모듈이 쓰는 공개 계약만,
실제 구현은 `<module>.internal`에 둔다. `com.ticket.core`는 production class가 없고, `storage`도
없다. 아직 모듈로 옮기지 않은 legacy 코드가 생기면 이 자리를 쓴다. `com.ticket.bootstrap`은 legacy가
아니다 — 여러 module의 internal을 동시에 참조해야만 배선할 수 있는 코드를 위한 영구
composition-root 예외 자리이며, 지금은 production class가 하나도 없다(전역 배선은 `config`가
갖고, 특정 모듈의 물건을 등록하는 배선은 그 모듈이 자기 안에서 한다).
결정 배경은 `docs/adr/0003-spring-modulith-application-module-boundaries.md`가 원본이다.

**무엇이 금지인지는 문서가 아니라 구조 테스트가 원본이다.** `com.ticket.ModularityTests`가 모듈
경계 위반을, 각 모듈의 `internal.web`/`internal.application`/`internal.domain`/
`internal.infrastructure` 배치는 관례가 정한다. 어떤 테스트가 무엇을 고정하는지는
`docs/testing.md`의 구조 테스트 표에 있다. 새 코드의 위치, 자주 틀리는 지점, 테스트가 실패했을 때
볼 곳은 **`/place-code` 스킬**이 원본이다.

## 검증

작업 성격에 맞게 **가장 좁은 검증부터** 실행한다. 무엇을 돌릴지, 통합 테스트 조건, 결과 보고
규칙은 **`/verify` 스킬**이 원본이다. **검증에 실패한 상태로 완료라고 하지 않는다.**

## 코드 리뷰

사람과 리뷰 봇이 같은 기준을 따른다.

- 패치만 보지 말고 주변 코드, 호출 흐름, 관련 설정, 관련 테스트까지 함께 읽는다.
- 취향성 스타일보다 실제 결함 가능성, 회귀 위험, 테스트 공백을 우선한다.
- 계층 경계, 보안, 동시성, `@Transactional` 경계, Redis TTL과 만료 처리를 항상 본다.
  `auth`·`hold`·`order`·`performanceseat`는 특히 상태 전이와 부분 실패를 확인한다.
- findings first, 심각도 순. 왜 문제이고 어떤 조건에서 깨지는지 짧게 적는다.
- 근거가 약한 코멘트는 남기지 않는다. 치명적 문제가 없으면 그 사실을 명시하고 남은 검증 공백을 적는다.

## 커밋 및 PR

- **커밋은 사용자가 명시적으로 요청할 때만 만든다.** "진행해 / 좋아"는 커밋 트리거가 아니다.
- 작업 브랜치에서 작업한다. 기본 브랜치의 커밋·push는 `scripts/hooks/guard-branch.sh`가 막는다.
  `master` push는 곧 운영 배포다.
- 하나의 커밋에는 하나의 목적만 담고, 기존 사용자 변경과 섞지 않는다.
- 이미 원격에 올라간 이력을 바꾸려면 먼저 사용자 승인을 받는다.

형식은 `<type>(<scope>): <한국어 설명>`이다. type 표, scope 기준, 절차와 충돌 검증은
**`/commit-pr` 스킬**이 원본이다.

## Agent skills

엔지니어링 스킬(`/triage`, `/to-tickets`, `/to-spec`, `/wayfinder`, `/domain-modeling` 등)이
이 저장소에서 쓸 설정이다.

- **이슈 트래커**: `ticket-project/ticket-core`의 GitHub Issues, `gh` CLI. `docs/agents/issue-tracker.md`
- **트리아지 라벨**: 표준 다섯 개를 기본 이름 그대로. `docs/agents/triage-labels.md`
- **도메인 문서**: 단일 컨텍스트. `CONTEXT.md`와 `docs/adr/`. `docs/agents/domain.md`
- **문서 ablation**: 지시를 지운 뒤 품질 회귀를 판정한다. `docs/agents/ablation-probes.md`
