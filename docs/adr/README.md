# ADR (Architecture Decision Record)

## 언제 ADR을 쓰는가

세 조건을 모두 만족할 때만 쓴다(Matt Pocock `domain-modeling` 스킬과 같은 기준).

1. 되돌리기 어렵다 — 스키마, 모듈 경계, 외부 계약처럼 바꾸는 비용이 크다.
2. 나중에 누군가 "왜 이렇게 했는가"를 물을 만하다.
3. 실제로 여러 대안을 검토했다 — 유일한 선택지였다면 ADR이 아니라 그냥 구현이다.

## 번호 규칙

순차 번호를 쓴다. 새 ADR을 쓰기 전에 `docs/adr/`에서 가장 큰 번호를 찾아 그 다음 번호를 쓴다.

## ADR 0004가 없는 이유

병렬 워크트리 하나가 2026-09-04 무렵 "ADR 0004(공통 코드/common codes)"를 작성하며 그 번호와
untracked Flyway `V2__create_common_codes.sql`을 선점했다. 이후 다른 동시 작업과 충돌해 이 작업은
중단됐고, 번호와 파일 모두 저장소에 반영되지 않았다. 남은 흔적은
`docs/agents/observed-failures.md`의 "병렬 워크트리가 같은 module Flyway 버전 번호를 잡았다"
기록뿐이다 — 무관하게 진행 중이던 Task 3/4의 `catalog` 모듈 `V2` 충돌을 다루며 "ADR 0004, 공통
코드"의 untracked `V2__create_common_codes.sql`과도 겹쳤다고 적혀 있다. 다음 ADR은 0004를 건너뛰고
0005부터 다시 쓴다.
