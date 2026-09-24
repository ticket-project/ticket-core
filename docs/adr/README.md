# ADR (Architecture Decision Record)

## 언제 ADR을 쓰는가

세 조건을 모두 만족할 때만 쓴다(Matt Pocock `domain-modeling` 스킬과 같은 기준).

1. 되돌리기 어렵다 — 스키마, 모듈 경계, 외부 계약처럼 바꾸는 비용이 크다.
2. 나중에 누군가 "왜 이렇게 했는가"를 물을 만하다.
3. 실제로 여러 대안을 검토했다 — 유일한 선택지였다면 ADR이 아니라 그냥 구현이다.

## 번호 규칙

순차 번호를 쓴다. 새 ADR을 쓰기 전에 `docs/adr/`에서 가장 큰 번호를 찾아 그 다음 번호를 쓴다.

## 탐색과 상태

번호는 기존 기록을 유지한다(0004는 발행된 기록이 없다). 개별 ADR의 제안·채택·대체 상태와 대체 범위를 본문 근거로 확인한다. 채택은 구현 완료를 뜻하지 않는다. 현재 구조는 [architecture.md](../architecture.md), 현재 예매 동작은 [core-booking-lifecycle.md](../core-booking-lifecycle.md)를 확인한다. 과거 타입 이름과 당시 판단은 기록으로 남긴다.
