# 커밋과 PR 컨벤션 (상세)

커밋 메시지와 PR 제목은 Conventional Commits를 기반으로 작성한다. 이 규칙은 사람과 AI 에이전트
모두에게 동일하게 적용한다.

## 기본 형식

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

## type

`type`은 변경 파일의 종류가 아니라 **변경 목적**을 기준으로 선택한다.

| type | 사용 기준 |
| --- | --- |
| `feat` | 새로운 기능 또는 외부 동작 추가 |
| `fix` | 잘못된 동작이나 결함 수정 |
| `refactor` | 기능 변경 없는 코드 구조 개선 |
| `perf` | 응답 시간, 처리량, 자원 사용 등 성능 개선 |
| `test` | 테스트만 추가하거나 수정 |
| `docs` | 문서만 변경 |
| `chore` | 제품 동작과 무관한 유지보수 작업 |
| `build` | Gradle, 의존성 또는 빌드 설정 변경 |
| `ci` | CI 워크플로우 변경 |
| `security` | 인증, 권한, 토큰 또는 secret 취급 강화 |
| `revert` | 기존 변경 되돌리기 |

같은 주문 코드를 건드려도 목적에 따라 type이 달라진다.

```text
새로운 주문 API 추가              -> feat(order)
주문 실패 처리 오류 수정          -> fix(order)
동작을 유지하며 주문 클래스 분리  -> refactor(order)
DB 커넥션 점유 시간 단축          -> perf(order)
```

## scope

`scope`는 변경의 주된 책임 영역이다. 다음 순서로 **가장 작은 적절한 범위**를 고른다.

1. 하나의 도메인 변경이면 도메인 이름을 쓴다.
2. 여러 도메인에 걸친 모듈 변경이면 모듈 이름을 쓴다.
3. 여러 Core 도메인과 모듈에 걸친 변경이면 `core`를 쓴다.
4. 저장소 전체 작업으로 특정 범위를 정하기 어렵다면 scope를 생략한다.

권장 도메인 scope:

```text
auth, member, show, performance, performanceseat, hold, order, queue
```

권장 모듈·기술 scope:

```text
booking, catalog, member, payment, shared, config, redis, logging, seed, tools, ci, review, codex
```

여러 Application Module에 걸친 구조 변경(Spring Modulith 경계, 전역 설정 등)은 특정 모듈
scope 대신 `modulith`를 쓰거나 scope를 생략한다. 계층형 시절의 `core-api`/`core-domain`/
`core-infra`는 legacy 코드(`com.ticket.core.*`)를 다룰 때만 쓴다.

기존 scope로 표현할 수 있으면 새 scope를 임의로 만들지 않는다. 새 scope가 필요하면 실제 도메인,
모듈 또는 안정적인 하위 시스템 이름을 쓴다.

## 설명과 본문

- 설명은 한국어로 작성하고 마침표를 붙이지 않는다.
- 기술 고유명사와 제품명은 `Redis`, `WebSocket`, `Flyway`처럼 원문 표기를 허용한다.
- `수정`, `개선`, `작업`처럼 대상이 드러나지 않는 표현만 쓰지 않는다.
- `추가`, `수정`, `분리`, `축소`, `최적화`처럼 변경 결과가 드러나는 표현을 쓴다.
- 하나의 커밋에는 하나의 목적만 포함한다. 목적이 다르면 커밋을 분리한다.

코드만 보고 이유를 알기 어려운 변경은 빈 줄 다음에 한국어 본문을 추가한다.

```text
perf(order): 주문 생성 트랜잭션 범위 축소

Redis 좌석 선점 중 DB 커넥션을 점유하지 않도록
주문과 선점 이력 저장 구간만 트랜잭션으로 분리한다.
```

호환성을 깨는 변경은 type 또는 scope 뒤에 `!`를 붙이고 본문 하단에 `BREAKING CHANGE:`를 쓴다.

```text
feat(order)!: 주문 생성 응답 형식 변경

BREAKING CHANGE: 기존 holdKey 응답 필드를 orderKey로 대체한다.
```

## PR

- PR 제목도 커밋과 동일한 `<type>(<scope>): <한국어 설명>` 형식을 쓴다.
- PR의 type과 scope는 개별 파일이 아니라 **PR 전체의 주된 목적과 영향 범위**를 기준으로 고른다.
- 성능 코드에 테스트와 문서가 함께 포함돼도 주된 목적이 성능 개선이면 `perf`를 쓴다.
- 서로 관계없는 기능, 버그 수정, 리팩터링이 섞이면 하나의 포괄적인 제목을 만들지 말고 PR을 분리한다.
- PR 본문은 `.github/pull_request_template.md`의 변경 목적, 주요 변경 내용, 영향 범위, 테스트와
  롤백 항목을 작성한다.

## AI 에이전트 작업 규칙

- 기존 히스토리를 추측만으로 모방하지 말고 이 문서의 type, scope, 언어 규칙을 우선 적용한다.
- type은 변경 목적, scope는 주된 책임 영역을 기준으로 고른다.
- 여러 커밋을 만들 때는 작업 책임별로 분리하고 각각의 메시지를 독립적으로 작성한다.
- 이미 푸시한 커밋 메시지를 변경하면 커밋 해시와 이후 이력이 바뀐다는 점을 설명하고 사용자
  승인 후 진행한다.
- force push가 필요하면 원격이 예상한 상태일 때만 갱신하는 `--force-with-lease`를 쓴다.
