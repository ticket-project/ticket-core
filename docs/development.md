# 개발 기준

이 문서는 현재 코드 기준 개발 맥락을 정리한다. 상세 구조는 [architecture.md](architecture.md), 실행과 검증은 [operations.md](operations.md)를 함께 본다.

## 프로젝트 요약

Ticket은 공연/전시 티켓팅 백엔드다. 현재 구현의 중심은 아래 흐름이다.

- 인증/회원: 이메일 회원가입, 로그인, JWT 갱신, OAuth2 로그인 URL 조회 및 토큰 교환
- 공연/전시 조회: 쇼, 장르, 메타 코드, 회차/좌석 레이아웃 조회
- 좌석 선택: Redis TTL 기반 임시 선택 상태와 WebSocket 전파
- 좌석 선점: Redis 기반 hold와 주문 시작 흐름
- 주문: `PENDING` 주문 생성, 조회, 취소, 만료 처리
- 대기열: Ticket Server가 회차별 DIRECT/QUEUE를 결정하고, `ticket-queue` 별도 서비스가 shard/local sequence와 public state 기반 대기 상태 및 admission token 발급을 담당

결제 승인/실패/콜백과 최종 판매 확정 흐름은 아직 별도 구현 대상이다.

## 주요 API 흐름

### 인증

- `POST /api/v1/auth/signup`
- `POST /api/v1/auth/login`
- `POST /api/v1/auth/refresh`
- `POST /api/v1/auth/logout`
- `GET /api/v1/auth/social/urls`
- `POST /api/v1/auth/oauth2/token`

구현 기준:

- API 인증은 JWT 기반 stateless 방식이다.
- OAuth2 인가 흐름은 별도 filter chain에서 처리한다.
- Refresh token과 OAuth2 1회용 코드는 Redis를 사용한다.
- 공개 GET API를 제외한 대부분 API는 인증이 필요하다.

주요 위치:

- `core/core-api/src/main/java/com/ticket/core/api/controller/AuthController.java`
- `core/core-api/src/main/java/com/ticket/core/config/security`
- `core/core-domain/src/main/java/com/ticket/core/domain/auth`
- `core/core-infra/src/main/java/com/ticket/core/infra/auth`

### 쇼/회차/좌석 조회

- 쇼 목록, 최신 쇼, 오픈 예정 쇼, 검색, 상세 조회
- 쇼 기준 좌석 정보와 공연장 레이아웃 조회
- 회차별 좌석 상태 조회
- 회차별 등급별 잔여 좌석 수 조회

구현 기준:

- 좌석 상태는 DB 상태와 Redis 점유 상태를 합쳐 계산한다.
- 잔여 좌석 수는 Redis `SELECTING`, `HOLDING` 상태를 반영한다.

주요 위치:

- `core/core-api/src/main/java/com/ticket/core/api/controller/ShowController.java`
- `core/core-api/src/main/java/com/ticket/core/api/controller/PerformanceController.java`
- `core/core-domain/src/main/java/com/ticket/core/domain/show`
- `core/core-domain/src/main/java/com/ticket/core/domain/performance`
- `core/core-domain/src/main/java/com/ticket/core/domain/performanceseat`

### 좌석 선택

- `POST /api/v1/performances/{performanceId}/seats/{seatId}/select`
- `DELETE /api/v1/performances/{performanceId}/seats/{seatId}/select`
- `DELETE /api/v1/performances/{performanceId}/seats/select`

정책:

- selection은 UX 보조 상태다.
- hold의 필수 선행 조건이 아니다.
- 다른 사용자가 selection 중이어도 hold가 성공할 수 있다.
- selection은 Redis TTL로 자동 만료된다.
- 만료 시 Redis expired listener가 `DESELECTED` 이벤트를 전파한다.

주요 위치:

- `core/core-api/src/main/java/com/ticket/core/api/controller/SeatSelectionController.java`
- `core/core-domain/src/main/java/com/ticket/core/domain/performanceseat/command`
- `core/core-infra/src/main/java/com/ticket/core/infra/performanceseat`

### 좌석 선점과 주문 시작

- `POST /api/v1/orders` (canonical)
- `POST /api/v1/performances/{performanceId}/holds` (`@Deprecated`, 내부적으로 동일한 `CreateOrderUseCase`로 위임)

현재 흐름:

1. 회차 유효성, 좌석 유효성, 수량 제한 검증
2. 같은 회원/같은 회차 `PENDING` 주문 여부 검증
3. Redis에 좌석 hold 생성
4. DB에 `PENDING` 주문 생성
5. hold history 기록
6. DB 커밋과 connection 반환
7. 제한된 background worker에서 selection 해제와 HELD 상태 전파
8. 201 Created와 X-Order-Key 헤더로 주문 식별자 반환

정책:

- 회차당 같은 회원은 `PENDING` 주문 1건만 허용한다.
- hold는 다중 좌석 all-or-nothing으로 생성한다.
- hold는 Redis TTL 만료와 주문 만료 흐름에 연결된다.
- 주문 저장 트랜잭션 안에서는 Redis 또는 WebSocket을 호출하지 않는다.
- 생성 후처리 실패는 이미 커밋된 주문과 hold를 되돌리지 않는다.
- 대기열이 필요한 회차는 `X-Admission-Token` 검증을 먼저 통과해야 한다.

주요 위치:

- `core/core-api/src/main/java/com/ticket/core/api/controller/OrderController.java`
- `core/core-api/src/main/java/com/ticket/core/api/controller/HoldController.java` (deprecated)
- `core/core-domain/src/main/java/com/ticket/core/domain/hold`
- `core/core-domain/src/main/java/com/ticket/core/domain/order/command/create`
- `core/core-infra/src/main/java/com/ticket/core/infra/hold`

### 주문

- `GET /api/v1/orders/{orderKey}`
- `DELETE /api/v1/orders/{orderKey}`

현재 구현:

- 주문 상세 조회
- 사용자 취소
- 스케줄러 기반 만료
- Redis expired listener 기반 즉시 만료
- hold creation/release outbox 기반 후처리 보강

- 취소와 만료는 OrderTerminationService의 공통 종료 절차를 사용한다.
- 주문 생성 시 PENDING 주문, hold history, hold creation outbox를 같은 DB 트랜잭션에서 기록한다.
- 주문 종료 시 상태 전이, hold history, hold release outbox를 같은 DB 트랜잭션에서 기록한다.
- outbox의 Redis/WebSocket 처리는 DB 트랜잭션 밖에서 실행한다.
- Redis 만료 listener와 주문 background worker는 각각 동시 실행 수를 제한한다.

주요 위치:

- `core/core-api/src/main/java/com/ticket/core/api/controller/OrderController.java`
- `core/core-domain/src/main/java/com/ticket/core/domain/order`
- `core/core-domain/src/main/java/com/ticket/core/domain/order/command/release`
- core/core-infra/src/main/java/com/ticket/core/infra/order
- docs/core-booking-lifecycle.md

### 대기열

대기열 런타임은 `ticket-queue` 독립 서비스가 담당한다. Core는 Queue Controller, queue token 저장소, queue token 만료 핸들러를 갖지 않는다. 대신 `PerformanceQueuePolicy`로 공연 상세 응답의 회차별 `entryType`을 계산하고, 클라이언트가 예매 버튼 클릭 시 DIRECT/QUEUE를 분기한다.

Queue Server hot path는 Core DB와 회차별 정책 snapshot을 조회하지 않는다. Queue Server는 모든 요청 회차에 애플리케이션 기본 입장 속도와 TTL을 적용하며, `join`에서 받은 `shardId`와 `localSeq`를 public `/state` 응답의 `serving[shardId]`와 비교해 입장 가능 여부를 판단한다.
Core는 Queue가 발급한 admission token의 서명, issuer, audience, scope, 만료 시각과 `memberId`, `performanceId` 일치 여부를 검증한다. 주문 생성 후 Queue Server에 session 완료 요청을 보내지는 않으며, 입장 후 shopping session은 Queue Server의 TTL로 정리된다.


주요 개념:

- 서명된 queue token (`X-Queue-Token`)
- shard/local sequence와 public state
- public state 기반 adaptive polling
- admission token
- admission token으로 보호 API 진입
- show detail entryType

주요 위치:

- 형제 저장소 `../ticket-queue`
- `core/core-api/src/main/java/com/ticket/core/config/admission/AdmissionTokenValidator.java`
- `core/core-api/src/main/java/com/ticket/core/config/admission/AdmissionTokenService.java`
- `core/core-api/src/main/java/com/ticket/core/config/admission/TicketAdmissionTokenProperties.java`

## 핵심 도메인 모델

### Order

- 내부 PK: `id`
- 외부 식별자: `orderKey`
- 주요 상태: `PENDING`, `CONFIRMED`, `EXPIRED`, `CANCELED`, `PAYMENT_FAILED`

주문 상태 모델은 결제 성공/실패를 수용할 수 있지만, 실제 결제 유스케이스는 아직 별도 구현 대상이다.

### Hold

- Redis 기반 임시 점유 상태
- DB에는 `HOLD_HISTORY` 이력 저장
- 주문 시작과 만료/취소 후처리에 직접 연결

### Selection

- Redis TTL 기반 UX 보조 상태
- 실제 점유 권리는 hold가 담당

### Queue

- 대기열 상태는 `ticket-queue`가 관리한다.
- Core는 예매 API 진입 시 회차 정책을 먼저 확인하고, 대기열이 필요한 회차에서만 `X-Admission-Token`의 서명, 만료, memberId와 performanceId 일치 여부를 검증한다.
- Core의 Redis는 좌석 선택, hold, refresh token, OAuth2 one-time auth code 용도로만 사용한다.

## 미구현 또는 후속 범위

- 결제 도메인, controller, callback, PG 연동
- 결제 성공 시 주문 확정과 최종 좌석 판매 확정
- Flyway 기반 운영 마이그레이션 스크립트 누적과 검증 환경 보강
- Redis key scan 기반 조회 구조 최적화
- 운영 관측성 대시보드와 알림 보강

## 작업 시작 전

1. 대상 도메인의 `command`, `query`, `model`, `repository`, `store`와 기존 테스트를 읽는다.
2. 바꿀 API의 요청·응답·오류 계약과 Swagger 문서 인터페이스를 확인한다.
3. 트랜잭션 경계, Redis key와 TTL, 만료 후처리 경로를 추적한다.
4. 같은 흐름을 검증하는 테스트가 어디에 있는지 확인한다. 없으면 그 공백을 작업 범위에 포함한다.
5. 프로파일별 설정(`application-local.yml`, `-dev.yml`, `-prod.yml`)에 영향이 있는지 본다.

## 기능 개발 순서

1. **계약 확정** — endpoint, 인증 요구, request/response, 상태 코드, 오류를 먼저 정한다.
2. **domain** — 엔티티와 도메인 규칙, 필요한 port(`store`, publisher, client)를 `core-domain`에 정의한다.
2-1. **application** — use case와 트랜잭션 경계를 `core-app`에 만든다. 도메인 규칙을 순서대로 엮는다.
3. **infrastructure** — `core-infra`에서 port를 구현한다. Redis 명령, WebSocket 발행, 외부 HTTP를 여기에 둔다.
4. **presentation** — `core-api`에서 요청 검증, principal 추출, use case 호출, 응답 매핑만 한다.
5. **migration** — DB 구조 변경이 있으면 Flyway 새 버전 파일을 추가한다([operations.md](operations.md#db-마이그레이션)).
6. **테스트** — 도메인 규칙, use case, adapter 계약, controller 계약을 채운다([testing.md](testing.md)).
7. **검증** — 좁은 검증부터 실행한다([testing.md](testing.md#무엇을-돌릴지)).

사용하지 않는 빈 계층 파일은 만들지 않는다. 다만 Controller에 업무 규칙을 넣거나 use case를 건너뛰고
Controller가 repository를 직접 부르는 형태로 합치지 않는다.

## 계층 책임

모듈 경계와 계층별 책임은 [architecture.md](architecture.md)가 단일 출처다. 새 코드를 어디에
둘지는 `AGENTS.md`의 판단표를, 상세 근거는 [architecture.md의 코드 위치 결정표](architecture.md#코드-위치-결정표)를
본다. 이 문서에는 기능 맥락과 작업 규칙만 둔다.

## Redis 작업 규칙

- key 조립과 TTL은 `core-infra`의 adapter가 소유하고 도메인은 port만 본다.
- 운영 Redis에서 `KEYS`를 사용하지 않는다. 필요한 조회는 인덱스(Sorted Set 등)로 만든다.
- key 형식이나 인덱스 구조를 바꾸면 기존 key가 남아 있는 상태의 전환 절차를 함께 설계한다.
  [operations.md의 좌석 선택 Redis 인덱스 전환](operations.md#좌석-선택-redis-인덱스-전환)이 선례다.
- TTL, expiration listener, scheduler 보정 중 하나만 바꾸지 않는다. 세 경로는 같은 정합성을 함께 지킨다.
- Core Redis의 용도는 seat selection, seat hold, refresh token, OAuth2 one-time auth code뿐이다.

## 분산락 작업 규칙

- 같은 회원·회차의 중복 주문 시작과 같은 좌석 동시 점유를 막는 데 사용한다.
- 락 범위 안에서 외부 I/O를 늘리지 않는다. 임계 구역은 짧게 유지한다.
- 락 키를 바꾸면 보호 대상이 그대로인지 테스트로 고정한다.

## 완료로 판정하지 않는 조건

- [architecture.md의 아키텍처 규칙](architecture.md#아키텍처-규칙) 중 하나라도 어겼다.
- `core-domain`에 `@Scheduled`, `@TransactionalEventListener`, Redisson, Spring Data Redis,
  `org.springframework.messaging`, Swagger import가 들어갔다.
- Controller가 repository나 Redis adapter를 직접 주입받는다.
- DB 트랜잭션 안에서 Redis 또는 WebSocket을 호출한다.
- 엔티티나 DB row를 응답으로 그대로 내보낸다.
- 이미 적용된 Flyway 파일을 수정했다.
- 오류를 빈 배열, `null`, 성공 응답으로 감춘다.
- 변경한 흐름에 대응하는 테스트가 없다.
- 관측 지표나 로그만 보고 정합성을 확인했다고 판단했다.

## 개발 시 주의점

- Controller에는 비즈니스 규칙이나 직접 저장소 접근을 넣지 않는다.
- Redis, WebSocket, 외부 HTTP, AOP 구현은 `core-infra`에 둔다.
- 도메인/application 코드는 port 인터페이스에 의존한다.
- hold, order, performanceseat, queue 변경은 동시성, TTL, 만료 후처리, 테스트 공백을 먼저 확인한다.
- API 요청/응답을 바꾸면 하위 호환성과 Swagger 문서 영향을 함께 본다.

## 커밋과 PR 절차

기본 흐름은 **작업 브랜치 → 작업 단위 커밋들 → PR → squash 또는 rebase 반영**이다.
기준 브랜치는 `master`(= `origin/HEAD`)이며 **`master`에 직접 커밋하지 않는다.**
이 저장소는 선형 이력을 유지하므로 **merge commit으로 반영하지 않고** `gh pr merge --squash` 또는
`--rebase`를 사용한다. 브랜치 최신화도 merge가 아니라 rebase로 한다.

커밋과 PR은 **사용자가 명시적으로 요청할 때만** 시작한다. "진행해 / 좋아" 같은 일반 승인은 작업을
계속하라는 뜻이지 커밋 트리거가 아니다.

### master push는 곧 배포다

`.github/workflows/deploy.yml`이 `master` push에 붙어 있다. 반영하면 다음이 자동으로 일어난다.

1. `ci.yml`이 `./gradlew test :core:core-infra:integrationTest :core:core-api:bootJar`를 실행하고 jar를 올린다.
2. 검증된 jar로 Docker 이미지를 빌드해 `ticket-be:<commit SHA>`로 push한다.
3. 운영 서버에 SSH로 들어가 `docker compose up -d`와 `docker restart ticket-nginx`를 실행한다.

자동 롤백은 없다. PR을 반영할 때 운영에 나간다는 사실을 먼저 알린다. DB 구조 변경이 포함되면 Flyway가
애플리케이션 기동 중 실행된다는 점을 함께 확인한다([operations.md](operations.md#db-마이그레이션)).

### 순서

1. **준비물 확인** — `gh auth status`(PR 단계 전), `git --version` 2.38 이상(충돌 검증에 필요).
2. **현황 파악** — `git status`와 `git diff`로 워킹트리 전체를 본다. untracked 파일도 내용을 확인한다.
   기존 미커밋 변경은 사용자의 작업으로 보고 되돌리지 않는다.
3. **범위별 검증** — 커밋이 건드리는 범위의 검증을 통과해야 커밋한다([testing.md](testing.md#무엇을-돌릴지)).
   실패하면 커밋하지 않고 실패 내용을 그대로 보고한다. 테스트 스킵과 `--no-verify`는 쓰지 않는다.
4. **작업 단위로 쪼개기** — 한 커밋은 하나의 의도이며 독립적으로 revert할 수 있는 덩어리다.
   성격이 다른 변경을 같은 커밋에 넣지 않는다. 하나의 PR에 여러 작업 단위 커밋이 담길 수 있고,
   그때는 PR 본문에 단위별로 정리한다.
5. **명시적 스테이징** — 파일 경로를 지정해 `git add`한다. `git add -A`와 `git add .`는 쓰지 않는다.
   `build/`, `.gradle/`, `docker-compose.yml`, 로컬 설정 파일은 스테이징하지 않는다.
6. **작업 브랜치** — `master`에 있으면 먼저 브랜치를 딴다.

   ```bash
   git fetch origin
   git checkout -b <prefix>/<주제> origin/HEAD
   ```

   `<prefix>`는 변경 성격(`feat`, `fix`, `refactor`, `chore`, `docs`, `ci`)이나 작업 주체(`claude`, `codex`, `agent`)를
   쓰고 주제는 영문 소문자와 하이픈으로 짧게 적는다. 기준 브랜치보다 뒤처져 있으면 `git rebase origin/HEAD`로
   정렬한다. `amend`는 요청받았을 때만 하고, force push는 자기 작업 브랜치에 한해 `--force-with-lease`로 한다.
7. **충돌 검증** — 커밋할 때마다 확인한다.

   ```bash
   git fetch origin
   git status -sb
   git merge-tree --write-tree origin/HEAD HEAD
   ```

   exit 0이면 통과다. exit 1이면 임의로 해결하지 않고 멈춘 뒤, 충돌 파일마다 ① 내 브랜치가 바꾼 내용
   ② `master`가 바꾼 내용을 각각 한 줄 한국어로 정리해 "내 변경 유지 / master 쪽 유지 / 둘을 합침" 중에서
   선택을 받는다. merge-tree 원문 덤프를 그대로 붙이지 않는다.
8. **PR 생성과 반영** — 제목은 아래 컨벤션과 같은 형식을 쓰고, 본문은
   `.github/pull_request_template.md`의 항목을 채운다. 반영은 `gh pr merge --squash` 또는 `--rebase`를
   사용하고 `--merge`는 쓰지 않는다.
9. **뒷정리와 보고** — 커밋 해시, 변경 통계, 검증 결과, 충돌 검증 결과, PR URL과 반영 여부를 보고하고,
   남은 unstaged 또는 untracked 파일이 있으면 반드시 알린다. 이번 작업과 무관한 미커밋 변경이 있으면
   정렬을 강행하지 않고 미룬 사실만 보고한다. stash나 reset으로 다른 작업을 건드리지 않는다.

### 하지 않을 것

- 명시적 요청 없이 커밋 절차를 시작하기
- `master`에 직접 커밋하거나 push하기
- merge commit으로 PR 반영하기, 브랜치 최신화를 merge로 하기
- 검증 미실행이나 실패 상태로 커밋하기
- 세션 작업 전체를 거대한 커밋 하나로 뭉치기
- 충돌 검증 실패 상태에서 반영 강행하기
- `git add -A`로 뭉텅이 스테이징하기, 확인하지 않은 파일 커밋하기
- 영어 커밋 메시지, "Update files" 류의 무의미한 제목
- `--no-verify`, 훅과 서명 우회

멀티라인 커밋 메시지는 bash heredoc으로 작성한다. PowerShell에서 heredoc 문법을 흉내 내지 않는다.

```bash
git commit -m "$(cat <<'EOF'
perf(order): 주문 생성 트랜잭션 범위 축소

Redis 좌석 선점 중 DB 커넥션을 점유하지 않도록
주문과 선점 이력 저장 구간만 트랜잭션으로 분리한다.

Co-Authored-By: <작업한 에이전트 표기>
EOF
)"
```

## 커밋과 PR 컨벤션

커밋 메시지와 PR 제목은 Conventional Commits를 기반으로 작성한다. 이 규칙은 사람과 AI 에이전트 모두에게 동일하게 적용한다.

### 기본 형식

```text
<type>(<scope>): <한국어 설명>
```

`scope`는 선택 사항이다.

```text
feat(order): 결제 대기 주문 생성
fix(performanceseat): 만료된 좌석 선택 상태 정리
refactor(order): 주문 생성 보상 흐름 분리
perf(core): 좌석 조회 병목 완화
test(order): 주문 생성 동시성 테스트 추가
docs: 커밋 및 PR 컨벤션 문서화
```

### type

`type`은 변경 파일의 종류가 아니라 변경 목적을 기준으로 선택한다.

| type | 사용 기준 |
| --- | --- |
| `feat` | 새로운 기능 또는 외부 동작 추가 |
| `fix` | 잘못된 동작이나 결함 수정 |
| `refactor` | 기능 변경 없는 코드 구조 개선 |
| `perf` | 응답 시간, 쿼리, 락, 메모리 등 성능 개선 |
| `test` | 테스트만 추가하거나 수정 |
| `docs` | 문서만 변경 |
| `chore` | 제품 동작과 무관한 유지보수 작업 |
| `build` | 빌드 설정 또는 의존성 변경 |
| `ci` | CI 워크플로우 변경 |
| `security` | 인증, 권한 또는 보안 정책 강화 |
| `revert` | 기존 변경 되돌리기 |

예를 들어 주문 코드를 변경했더라도 목적에 따라 type이 달라진다.

```text
새로운 주문 API 추가              -> feat(order)
주문 실패 처리 오류 수정         -> fix(order)
동작을 유지하며 주문 클래스 분리 -> refactor(order)
DB 커넥션 점유 시간 단축         -> perf(order)
```

### scope

`scope`는 변경의 주된 책임 영역을 나타낸다. 다음 순서로 가장 작은 적절한 범위를 선택한다.

1. 하나의 도메인 변경이면 도메인 이름을 사용한다.
2. 여러 도메인에 걸친 모듈 변경이면 모듈 이름을 사용한다.
3. 여러 Core 도메인과 모듈에 걸친 변경이면 `core`를 사용한다.
4. 저장소 전체 작업으로 특정 범위를 정하기 어렵다면 scope를 생략한다.

권장 도메인 scope:

```text
auth, member, show, performance, performanceseat, hold, order, queue
```

권장 모듈·기술 scope:

```text
core, core-api, core-domain, core-infra, redis, logging, seed, tools, ci, review, codex
```

기존 scope로 표현할 수 있으면 새로운 scope를 임의로 만들지 않는다. 새로운 scope가 필요하면 실제 도메인, 모듈 또는 안정적인 하위 시스템 이름을 사용한다.

### 설명과 본문

- 설명은 한국어로 작성하고 마침표를 붙이지 않는다.
- 기술 고유명사와 제품명은 `Redis`, `WebSocket`, `Flyway`처럼 원문 표기를 허용한다.
- `수정`, `개선`, `작업`처럼 대상이 드러나지 않는 표현만 사용하지 않는다.
- `추가`, `수정`, `분리`, `축소`, `최적화`처럼 변경 결과가 드러나는 표현을 사용한다.
- 하나의 커밋에는 하나의 목적만 포함한다. 목적이 다르면 커밋을 분리한다.

코드만 보고 이유를 알기 어려운 변경은 빈 줄 다음에 한국어 본문을 추가한다.

```text
perf(order): 주문 생성 트랜잭션 범위 축소

Redis 좌석 선점 중 DB 커넥션을 점유하지 않도록
주문과 선점 이력 저장 구간만 트랜잭션으로 분리한다.
```

호환성을 깨는 변경은 type 또는 scope 뒤에 `!`를 붙이고 본문 하단에 `BREAKING CHANGE:`를 작성한다.

```text
feat(order)!: 주문 생성 응답 형식 변경

BREAKING CHANGE: 기존 holdKey 응답 필드를 orderKey로 대체한다.
```

### PR

- PR 제목도 커밋과 동일한 `<type>(<scope>): <한국어 설명>` 형식을 사용한다.
- PR의 type과 scope는 개별 파일이 아니라 PR 전체의 주된 목적과 영향 범위를 기준으로 선택한다.
- 성능 코드에 테스트와 문서가 함께 포함돼도 주된 목적이 성능 개선이면 `perf`를 사용한다.
- 서로 관계없는 기능, 버그 수정, 리팩터링이 섞이면 하나의 포괄적인 제목을 만들지 말고 PR을 분리한다.
- PR 본문은 `.github/pull_request_template.md`의 변경 목적, 주요 변경 내용, 영향 범위, 테스트와 롤백 항목을 작성한다.

### AI 에이전트 작업 규칙

- 기존 히스토리를 추측만으로 모방하지 말고 이 문서의 type, scope, 언어 규칙을 우선 적용한다.
- type은 변경 목적, scope는 주된 책임 영역을 기준으로 선택한다.
- 여러 커밋을 만들 때는 작업 책임별로 분리하고 각각의 메시지를 독립적으로 작성한다.
- 이미 푸시한 커밋 메시지를 변경하면 커밋 해시와 이후 이력이 바뀐다는 점을 설명하고 사용자 승인 후 진행한다.
- force push가 필요하면 원격이 예상한 상태일 때만 갱신하는 `--force-with-lease`를 사용한다.
