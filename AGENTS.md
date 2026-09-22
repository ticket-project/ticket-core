# Ticket 저장소 작업 가이드

사람과 AI 에이전트가 공통으로 따르는 **모든 도구의 단일 진입점**이다(Claude Code는 루트
`CLAUDE.md`의 `@AGENTS.md` 임포트로 읽는다).

**이 파일은 어디를 볼지만 정한다.** 규칙 본문은 아래 표가 가리키는 문서·스킬·테스트가
원본이다. 옮겨 적지 않는다 — 옮겨 적는 순간 원본과 어긋나고, 어긋난 쪽을 사람이 먼저 믿는다.

**클론 직후 한 번** `bash scripts/link-agent-skills.sh`(Windows `scripts\link-agent-skills.cmd`).

## 기본 원칙

- 설계 질문에는 여러 방안과 장단점, 추천을 함께 준다. 고르는 것은 사용자다.
- 문서와 응답은 한국어로 쓴다. 파일은 UTF-8, BOM 없이 유지한다.
- 요청되지 않은 기능 추가, 대규모 리팩터링, 부가 추상화는 하지 않는다.
- 기존 미커밋 변경은 사용자 작업으로 보고 되돌리지 않는다.
- **주변 코드를 선례로 복제하지 않는다.** 구조·이름·의존 방향의 원본은 아래 표가 가리키는
  문서와 executable test다. 지금 그렇게 되어 있다는 것이 그래도 된다는 근거는 아니다.
- **다른 module은 공개 Named Interface로만 부른다.** 구현 package를 직접 참조하지 않는다.
- **공유 코드를 먼저 `shared`에 넣지 않는다.** owner module을 먼저 찾고, 독립된 둘 이상이 같은
  의미로 쓰며 업무 어휘가 없을 때만 승격한다. `common`/`util`/`helper` package는 만들지 않는다.
- **코드 형식은 `.editorconfig`와 Gradle Spotless 설정이 정한다.** 포맷터 이름과 세부 옵션을
  여기 적지 않는다 — `build.gradle`과 이중 관리하게 된다.
- **범용 설계·질문·구현·리뷰 workflow는 Matt Pocock Skills(`.agents/skills/`)가 담당한다.**
  이 저장소에만 있는 사실은 아래 표가 가리키는 곳에만 둔다 — Skill에 다시 복사하지 않는다.
- 에이전트가 틀린 것을 발견하면 `docs/agents/observed-failures.md`에 적고 테스트·훅·규칙 중
  하나로 옮긴다. 대화에서만 고치고 끝내지 않는다.

## 먼저 읽을 문서

| 물음 | 원본 |
| --- | --- |
| 도메인 용어의 의미 | `CONTEXT.md` |
| 현재 BC·Aggregate·Module 구조, 의존 DAG | `docs/architecture.md` |
| Java 이름·패키지·포맷·테스트 관례 | `docs/code-conventions.md` |
| 새 class를 만들지, UseCase·조회 코드를 어떻게 쓸지 | `docs/readability-guidelines.md` |
| 중요한 결정을 왜 내렸는가 | `docs/adr/` |
| 예매·hold 생명주기와 event 후속 처리 | `docs/core-booking-lifecycle.md` |
| 무엇을 검증할지, 결과 보고 규칙 | **`/verify` 스킬** |
| 새 테스트를 어디에 어떻게 쓰는지 | `docs/testing.md` |
| 로컬 실행, 프로파일, Flyway, 배포, 관측 | `docs/operations.md` |
| 부하 테스트 | **`/loadtest` 스킬** |
| 새 개념의 BC·Aggregate 경계 판단 | `docs/architecture.md`의 Aggregate Rules |
| 기술 부채·미결 제품 결정 | `docs/technical-debt.md` |
| 미구현 기능 | GitHub Issues |
| 반복해 틀리는 지점 | `docs/agents/observed-failures.md`(임시 inbox — 반영되면 지운다) |

**현재 사실은 문서보다 코드와 executable test가 우선이다** — `com.ticket.ModularityTests`,
`DomainIsolationTest`, `AggregateAssociationTest`가 모듈 경계·aggregate 규칙을 강제한다.
완료·폐기된 기록은 문서로 남기지 않는다 — 그 자리는 `git log`다.

부하 테스트의 실제 시나리오와 실행 옵션은 형제 저장소 `../gatling-test`를 기준으로 본다.

## 검증

가장 좁은 검증부터 실행한다. **검증에 실패한 상태로 완료라고 하지 않는다.**

## 코드 리뷰

사람과 리뷰 봇이 같은 기준을 따른다. 패치만 보지 말고 주변 코드·호출 흐름·관련 테스트까지
읽는다. 취향성 스타일보다 실제 결함 가능성·회귀 위험·테스트 공백을 우선한다. findings first,
심각도 순 — 근거가 약한 코멘트는 남기지 않는다.

## 커밋 및 PR

- **커밋은 사용자가 명시적으로 요청할 때만 만든다.** "진행해 / 좋아"는 트리거가 아니다.
- 작업 브랜치에서 작업한다. **`master` push는 곧 운영 배포다**(`.github/workflows/deploy.yml`,
  자동 롤백 없음) — `scripts/hooks/guard-branch.sh`가 기본 브랜치 커밋·push를 막는다.
- 형식은 `<type>(<scope>): <한국어 설명>`(예: `fix(performanceseat): 만료된 좌석 선택 상태 정리`).
  `scope`는 도메인 > 모듈 > 생략 순으로 가장 좁게. type은 표준 11종 + `security`.
- 반영 전 `git merge-tree --write-tree origin/HEAD HEAD`로 충돌을 미리 확인한다.
- **하나의 커밋에 하나의 목적만 담고 항상 잘게 나눈다.** 전부 끝낸 뒤 몰아서 커밋하지 않는다.
  각 커밋은 그 자체로 컴파일되고 테스트를 통과해야 한다.

## Agent skills

Matt Pocock 엔지니어링 스킬(`.agents/skills/`)이 이 저장소에서 쓸 설정이다. 이슈 트래커는
`ticket-project/ticket-core`의 GitHub Issues + `gh` CLI(`docs/agents/issue-tracker.md`), 트리아지
라벨은 표준 다섯 개(`docs/agents/triage-labels.md`), 도메인 문서는 `CONTEXT.md`와 `docs/adr/`
(`docs/agents/domain.md`)다.
