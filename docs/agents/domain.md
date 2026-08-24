# 도메인 문서

엔지니어링 스킬이 이 저장소를 탐색할 때 도메인 문서를 어떻게 읽어야 하는지 정한다.

이 저장소는 **단일 컨텍스트**다. 루트 `CONTEXT.md` 하나와 `docs/adr/`를 쓴다.
Gradle 멀티모듈(`core/core-api`, `core/core-domain`, `core/core-infra`, `storage/redis-core`,
`support/logging`)은 계층 분리이지 별개의 바운디드 컨텍스트가 아니다.

## 탐색 전에 읽을 것

- 루트 **`CONTEXT.md`**: 도메인 용어집과 경계.
- **`docs/adr/`**: 지금 손대려는 영역과 닿는 ADR을 읽는다.
- 루트에 **`CONTEXT-MAP.md`**가 생겼다면 멀티 컨텍스트로 전환한 것이다. 맵이 가리키는
  컨텍스트별 `CONTEXT.md` 중 주제와 관련된 것을 모두 읽고, 컨텍스트 안의 `docs/adr/`도 확인한다.

**이 파일들이 없으면 조용히 넘어간다.** 없다고 지적하지 않고, 먼저 만들자고 제안하지도 않는다.
용어나 결정이 실제로 정리되는 시점에 `/domain-modeling`(`/grill-with-docs`,
`/improve-codebase-architecture`를 통해 도달)이 필요한 만큼만 만든다.

`docs/architecture.md`, `docs/development.md` 같은 기존 문서는 이것과 별개다. 그쪽은 모듈 경계와
구현 판단 기준이고, `CONTEXT.md`는 도메인 용어와 개념 경계를 다룬다. 어떤 작업에 어느 문서를 먼저
열지는 루트 `AGENTS.md`의 표를 따른다.

## 파일 구조

```
/
├── CONTEXT.md
├── docs/adr/
│   ├── 0001-....md
│   └── 0002-....md
├── core/
├── storage/
└── support/
```

## 용어집의 어휘를 쓴다

출력에서 도메인 개념을 이름으로 부를 때(이슈 제목, 리팩터링 제안, 가설, 테스트 이름)
`CONTEXT.md`에 정의된 용어를 쓴다. 용어집이 명시적으로 피하는 동의어로 흘러가지 않는다.

필요한 개념이 아직 용어집에 없다면 그 자체가 신호다. 프로젝트가 쓰지 않는 언어를 만들어내는
중이거나(다시 생각한다), 실제로 공백이 있는 것이다(`/domain-modeling`을 위해 적어둔다).

## ADR과 충돌하면 드러낸다

출력이 기존 ADR과 어긋나면 조용히 덮지 않고 명시한다.

> _ADR-0007(event-sourced orders)과 충돌하지만, 다시 논의할 가치가 있는 이유는…_
