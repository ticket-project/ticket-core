# 기여 가이드

사람이 저장소에 처음 들어왔을 때의 진입점이다. **규칙 본문은 여기 없다** — 아래 링크가 원본이고,
이 문서는 어디를 볼지와 무엇을 돌릴지만 알려준다.

AI 에이전트와 코딩 도구는 [`AGENTS.md`](AGENTS.md)를 진입점으로 쓴다. 두 문서는 같은 원본을
가리키며 서로를 복사하지 않는다.

## 개발 환경

JDK 25, Redis 7, Gradle wrapper. 설치와 실행 절차는 [`README.md`](README.md)와
[`docs/operations.md`](docs/operations.md)에 있다.

클론 직후 한 번: `bash scripts/link-agent-skills.sh`(Windows는 `scripts\link-agent-skills.cmd`).

## 무엇을 먼저 읽는가

| 물음 | 원본 |
| --- | --- |
| 도메인 용어 | [`CONTEXT.md`](CONTEXT.md) |
| 모듈·Aggregate 구조와 의존 방향 | [`docs/architecture.md`](docs/architecture.md) |
| 이름·패키지·형식 관례 | [`docs/code-conventions.md`](docs/code-conventions.md) |
| 새 class를 만들지, 조회를 어떻게 쓸지 | [`docs/readability-guidelines.md`](docs/readability-guidelines.md) |
| 테스트를 어디에 어떻게 쓰는지 | [`docs/testing.md`](docs/testing.md) |
| 로컬 실행·프로파일·배포·관측 | [`docs/operations.md`](docs/operations.md) |
| 왜 그렇게 결정했는가 | [`docs/adr/`](docs/adr/) |
| 아직 해결하지 않은 것 | [`docs/technical-debt.md`](docs/technical-debt.md) |

**현재 사실은 문서보다 코드와 실행 가능한 테스트가 우선이다.** 문서와 테스트가 다르면 테스트가
맞다고 보고, 문서를 고치거나 근거를 들어 테스트 수정을 제안한다.

## 코드 형식

`./gradlew spotlessApply`로 맞추고 `./gradlew spotlessCheck`로 검사한다. 원본은 `build.gradle`의
Spotless 설정이다 — 포맷터 이름과 옵션을 외울 필요가 없고, 줄바꿈 위치도 사람이 정하지 않는다.
`.editorconfig`는 IDE가 치는 모양을 그 결과에 미리 맞춰 둔 것이다.

## 검증

가장 좁은 것부터 돌린다. **검증에 실패한 상태로 완료라고 하지 않는다.**

```bash
./gradlew test --tests 'com.ticket.<바꾼 범위>*'   # 해당 범위
./gradlew build                                    # 전체
bash scripts/check-docs.sh                         # 문서 구조
```

구조를 바꿨다면 `ArchitectureRulesTest`, `ModularityTests`, `DomainIsolationTest`,
`AggregateAssociationTest`가 경계를 강제하므로 함께 돌린다.

## 바꿀 때 함께 확인할 것

| 무엇을 바꿨나 | 함께 볼 것 |
| --- | --- |
| DB schema / migration | [`docs/operations.md`](docs/operations.md)의 Flyway 절 |
| 공개 API 응답 | `*ContractTest`와 [`docs/testing.md`](docs/testing.md) |
| 모듈 경계·공개면 | `ModularityTests`, `ArchitectureRulesTest`의 승인 목록 |
| Redis key / TTL | [`docs/operations.md`](docs/operations.md) — 기존 key 호환성 |
| event class 위치 | Spring Modulith publication registry가 FQCN을 저장한다. 이동은 운영 데이터를 깨뜨릴 수 있다 |

## 커밋과 PR

- 형식은 `<type>(<scope>): <한국어 설명>`. 자세한 규칙은 [`AGENTS.md`](AGENTS.md)에 있다.
- **하나의 커밋에 하나의 목적만 담고 잘게 나눈다.** 각 커밋은 그 자체로 컴파일되고 테스트를 통과해야 한다.
- 작업 브랜치에서 작업한다.

> **`master` push는 곧 운영 배포다.** `.github/workflows/deploy.yml`이 자동 롤백 없이 배포한다.
> `scripts/hooks/guard-branch.sh`가 기본 브랜치 커밋·push를 막는다.
