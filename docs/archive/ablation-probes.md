# Ablation 프로브

문서에서 지시를 지운 뒤 **에이전트 품질이 실제로 떨어졌는지** 판정하는 회귀 프로브다.
지우기 전과 후에 같은 과제를 돌려 비교한다.

## 왜 이것부터 고정하는가

이 저장소는 지시를 늘 추가만 했고 지워본 적이 없다. 그래서 어떤 줄이 실제로 일하고 있고
어떤 줄이 관성인지 아무도 모른다. 판정 수단이 없으면 삭제는 도박이고, 결국 아무도 지우지 않는다.

프로브는 이 저장소의 첫 eval이기도 하다. 문서와 프롬프트는 모델이 바뀔 때마다 흔들리지만
"이 과제를 시켰을 때 파일이 어디에 놓여야 하는가"는 그보다 오래 산다.

## 어떻게 돌리는가

**반드시 새 세션에서 돌린다.** 이 문서를 읽은 세션은 답을 이미 본 셈이라 판정이 오염된다.
과제 문장만 그대로 주고, 에이전트가 알아서 하게 둔다. 힌트를 주지 않는다.

각 프로브는 **구현까지 시키지 않는다.** "어디에 무엇을 만들 것인지" 계획만 받아도
경계 판단은 드러난다. 판정이 애매하면 그때만 구현시키고 구조 테스트를 돌린다.

```bash
./gradlew test --tests "com.ticket.ModularityTests"
./gradlew test --tests "com.ticket.*.*ModuleTests"
./gradlew test --tests "com.ticket.core.CoreLayerArchitectureTest"
```

> **2026-09-02 갱신**: 단일 Gradle 프로젝트 전환(Spring Modulith)으로 `:core:core-api:test` 같은
> subproject 명령은 더 이상 없다. 아래 A/B/C 프로브의 "합격/불합격 신호"는 이번 전환 이전에
> 작성된 것이라 `core-app`/`core-domain`/`core-infra`/`bootstrap` 같은 계층형 모듈 이름을 쓴다.
> 새로 프로브를 돌릴 때는 이 이름을 소유 Application Module의 `internal.application`/
> `internal.domain`/`internal.infrastructure`로, `bootstrap`을 소유 모듈의 `internal.web`이나
> 전역 설정으로 바꿔 읽는다. 표는 그대로 두고(무엇을 측정했는지의 기록이므로) 판정할 때만
> 새 경로로 치환한다.

## 프로브

### A. use case 추가 — 계층 배치

> PerformanceSeat의 좌석 등급을 변경하는 use case를 추가해라.

| 합격 | 불합격 신호 |
| --- | --- |
| use case가 `core-app`의 `performanceseat.command` | Controller나 `core-domain`에 use case를 만든다 |
| 등급 변경 규칙 판단이 `core-domain` | 규칙을 use case 안에 인라인으로 넣는다 |
| 저장 포트가 `core-domain`의 `repository`, 구현이 `core-infra` | 포트에 Spring Data 타입이 들어간다 |
| 요청 DTO가 문자열을 받고 변환이 `core-app` | 도메인 enum을 Controller 시그니처에 노출한다 |

### B. 설정값 분리 — 기술과 업무의 경계

> Redis hold TTL을 하드코딩 대신 설정값으로 빼라.

| 합격 | 불합격 신호 |
| --- | --- |
| `@ConfigurationProperties`와 주입이 `core-infra` | `core-app`이나 `core-domain`이 설정을 직접 읽는다 |
| `core-app`이 TTL을 알아야 하면 발급한 쪽이 결과에 담아 돌려준다 | 양쪽이 각자 설정을 읽어 어긋난다 |
| 기본값과 프로파일별 값이 `bootstrap`의 `application*.yml` | 설정 파일을 `core-*` 모듈에 새로 만든다 |

### C. 조회 조건 추가 — Querydsl 격리

> 쇼 목록 조회에 정렬 조건을 하나 더해라.

| 합격 | 불합격 신호 |
| --- | --- |
| Querydsl 조건·정렬 조립이 `core-infra`의 `show.query` | use case가 `OrderSpecifier`를 조립한다 |
| `core-app`은 정렬 키를 뜻하는 값 타입만 다룬다 | `core-app`에 Querydsl import가 생긴다 |
| 커서 문자열 처리가 `core-api`에 남는다 | 커서 codec이 `core-app`으로 내려온다 |

## 기준선 (2026-08-28, 삭제 전)

| 측정 | 값 |
| --- | --- |
| 항상 로드되는 저장소 지침 | `AGENTS.md` 195줄 + `CLAUDE.md` 8줄 + output-style 48줄 = **18,246 bytes** |
| 온디맨드 문서 6종 | architecture 687 / development 289 / operations 286 / validation 159 / lifecycle 149 / testing 102 = **1,672줄** |
| `docs/architecture.md` | 687줄 / 36,368 bytes |

목표: 항상 로드 **10,000 bytes 이하**, `AGENTS.md` **80줄 이하**, `docs/architecture.md` **250줄 이하**.

행동 기준선(프로브 A/B/C의 삭제 전 결과)은 새 세션에서 측정해 아래에 추가한다.

## 결과 기록

프로브를 돌린 뒤 여기에 한 줄씩 남긴다. 회귀가 나오면 지운 줄을 되살리되
`[관측 YYYY-MM-DD]` 태그를 달고 `observed-failures.md`에 항목을 만든다.

| 날짜 | 커밋 | A | B | C | 조치 |
| --- | --- | --- | --- | --- | --- |
| | | | | | |
