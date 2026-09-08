# 도메인 문서

엔지니어링 스킬이 이 저장소를 탐색할 때 도메인 문서를 어떻게 읽어야 하는지 정한다.

이 저장소는 **단일 컨텍스트**다 — 컨텍스트가 하나(=BC가 하나)라는 뜻이 아니라, 도메인 문서를
루트 `CONTEXT.md` 한 파일과 `docs/adr/` 하나로 관리한다는 뜻이다. 단일 Gradle 프로젝트 안의 Spring
Modulith Application Module(기술 모듈 `shared`/`web`/`error`/`config`/`seed` 제외 —
`booking`/`show`/`venue`/`favorite`/`member`/`payment`)은 각각 하나의 Bounded Context(BC)와
일치한다([ADR 0006](../adr/0006-bounded-context-module-boundaries.md)). BC가 여섯 개로 늘었다고
컨텍스트별 문서로 쪼개지 않는다 — BC 목록·의존 관계·Aggregate 경계는
`docs/architecture.md`의 "Bounded Context와 Aggregate" 절이 요약한다.

## 탐색 전에 읽을 것

- 루트 **`CONTEXT.md`**: 도메인 용어집. BC·Aggregate 경계는 `docs/architecture.md`가 원본이다.
- **`docs/adr/`**: 지금 손대려는 영역과 닿는 ADR을 읽는다.
- 루트에 **`CONTEXT-MAP.md`**가 생겼다면 멀티 컨텍스트로 전환한 것이다. 맵이 가리키는
  컨텍스트별 `CONTEXT.md` 중 주제와 관련된 것을 모두 읽고, 컨텍스트 안의 `docs/adr/`도 확인한다.

**이 파일들이 없으면 조용히 넘어간다.** 없다고 지적하지 않고, 먼저 만들자고 제안하지도 않는다.
용어나 결정이 실제로 정리되는 시점에 `/domain-modeling`(`/grill-with-docs`,
`/improve-codebase-architecture`를 통해 도달)이 필요한 만큼만 만든다. BC·Aggregate 경계를
판단하거나 옮기는 작업은 `/domain-driven-development`가 원본이다 — 그 스킬은 `/domain-modeling`이
정리한 용어와 ADR을 읽고 판단에 쓴다.

`docs/architecture.md`, `docs/development.md` 같은 기존 문서는 이것과 별개다. 그쪽은 BC·Aggregate
경계를 포함한 모듈 경계와 구현 판단 기준이고, `CONTEXT.md`는 순수하게 도메인 용어만 다룬다. 어떤
작업에 어느 문서를 먼저 열지는 루트 `AGENTS.md`의 표를 따른다.

## 파일 구조

```
/
├── CONTEXT.md
├── docs/adr/
│   ├── 0001-....md
│   ├── ...
│   └── 0006-....md
└── src/main/java/com/ticket/
    └── booking/, show/, venue/, favorite/, member/, payment/   # BC
        shared/, web/, error/, config/, seed/                  # 기술 모듈(BC 아님)
```

legacy 패키지(`core`/`bootstrap`/`storage`/`support`)는 main 소스에서 모두 비워지거나 제거됐다
(`docs/architecture.md`의 "프로젝트 구조" 참고). test 소스의 공유 지원 클래스
(`src/test/java/com/ticket/core/infra/support/`)만 아직 남아 있다 — 새 module 코드가 아니다.

## 용어집의 어휘를 쓴다

출력에서 도메인 개념을 이름으로 부를 때(이슈 제목, 리팩터링 제안, 가설, 테스트 이름)
`CONTEXT.md`에 정의된 용어를 쓴다. 용어집이 명시적으로 피하는 동의어로 흘러가지 않는다.

필요한 개념이 아직 용어집에 없다면 그 자체가 신호다. 프로젝트가 쓰지 않는 언어를 만들어내는
중이거나(다시 생각한다), 실제로 공백이 있는 것이다(`/domain-modeling`을 위해 적어둔다).
리팩터링 여부를 판단해야 하면 `/domain-driven-development`를 쓴다.

## ADR과 충돌하면 드러낸다

출력이 기존 ADR과 어긋나면 조용히 덮지 않고 명시한다.

> _ADR-0007(event-sourced orders)과 충돌하지만, 다시 논의할 가치가 있는 이유는…_
