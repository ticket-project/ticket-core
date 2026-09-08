---
name: domain-driven-development
description: >
  ticket 저장소에서 새 도메인 개념의 소유 Bounded Context(BC)를 정하거나, Aggregate 경계를
  판단하거나, 기존 업무 책임을 다른 BC로 옮기는 결정을 내릴 때 쓴다. "이게 같은 aggregate인가",
  "이 개념은 어느 BC 소유인가", "이 정책을 다른 모듈로 옮겨도 되는가" 같은 질문에 답할 때, 그리고
  AggregateAssociationTest나 DomainPurityTest가 실패했을 때 쓴다. 코드를 어느 모듈·계층에 둘지는
  이미 정해진 경계 안에서의 배치이므로 `/place-code`를 쓰고, 용어 정의와 ADR 작성 자체는
  `/domain-modeling`을 쓴다. 일반 버그 수정, 스타일 변경, UI 작업에는 쓰지 않는다.
paths: src/main/java/com/ticket/**, src/test/java/com/ticket/**, CONTEXT.md, docs/adr/**, docs/architecture.md
allowed-tools: Bash(rg:*) Bash(./gradlew:*)
---

# 경계를 판단한다

이 스킬은 **모듈이 아직 정해지지 않았거나, 정해진 모듈이 틀렸을 수도 있는** 상황을 다룬다.
모듈이 이미 확정된 뒤 어느 계층·패키지에 코드를 둘지는 `/place-code`가 원본이다.

## 0. 원본을 먼저 읽는다

현재 BC·Aggregate 목록을 이 스킬에 복사하지 않는다. 복사한 목록은 반드시 낡는다 — 지금
`/place-code`가 겪고 있는 문제가 정확히 그것이었다. 판단하기 전에 항상 원본을 연다.

1. **`../../../docs/architecture.md`의 "Bounded Context와 Aggregate" 절** — 현재 BC 목록과
   소유, Aggregate root 목록과 나누는 기준.
2. **같은 문서의 "경계별 참조와 Repository 소유" 절** — 층위별 참조 방식과 Repository 소유.
3. **`../../../docs/adr/`** — 지금 손대려는 영역과 닿는 ADR. 특히
   [ADR 0006](../../../docs/adr/0006-bounded-context-module-boundaries.md)이 BC 재편 전체의
   배경이다.
4. **`../../../CONTEXT.md`** — 도메인 용어. 개념을 부를 때 여기 정의된 말을 쓴다.

## 1. 업무 능력을 먼저 찾는다

경계를 정하기 전에 이 규칙이 실제로 무엇을 하는지 확인한다.

- 이 규칙은 어떤 업무 결정을 내리는가?
- 무엇이 바뀔 때 함께 바뀌는가?
- 이 개념이 없어지면 어떤 업무가 먼저 깨지는가?
- 같은 단어가 다른 영역에서 다른 뜻으로 쓰이고 있는가?

## 2. Bounded Context를 결정한다

테이블 위치, 현재 패키지, 소비자 수만으로 BC를 정하지 않는다. 다음을 기준으로 판단한다.

- 유비쿼터스 언어(`CONTEXT.md`의 용어)
- 업무 정책의 결정권자
- 생명주기
- 변경 이유
- 다른 개념과의 결합도

**나눌 근거가 없으면 나누지 않는다.** ADR 0006이 Order/Ticket을 별도 BC로 쪼개지 않은 이유가
이 기준이다 — "다른 팀이 소유한다, 다른 배포 주기를 갖는다, 다른 방향의 확장이 예상된다"는
근거가 없으면 지금 나누지 않고 관측만 남긴다(ADR 0006 §4).

## 3. Aggregate를 결정한다

**API 응답 모양이나 테이블 관계로 판단하지 않는다.** 판단 기준은 함께 지켜야 하는 불변식과
트랜잭션 경계다.

- 한 트랜잭션에서 반드시 함께 일관돼야 하는가?
- 자식이 부모 없이 존재할 수 있는가? (없으면 같은 aggregate — `OrderSeat`/`Order`,
  `MemberSocialAccount`/`Member`, `PerformanceGrade`/`Performance`가 이 형태다)
- 수가 많거나 독립적으로 경합하는가? (그러면 분리 — `Venue`/`Seat`,
  `Performance`/`PerformanceSeat`가 이 형태다. 공연장 하나에 좌석이 수천 개라 한 aggregate로
  묶으면 로딩과 락 범위가 함께 커지고, 좌석 한 자리를 파는 데 회차 전체가 잠긴다)

조회 전용 모델은 여러 Aggregate의 데이터를 자유롭게 조합할 수 있다 — 이 판단의 대상이 아니다.

## 4. 참조 방향을 결정한다

- 같은 aggregate 안: entity 연관관계 가능, root만 Repository를 가진다.
- 같은 BC 다른 aggregate: scalar ID로 참조한다(권장).
- 다른 BC: scalar ID로 참조한다(강제). 상대 BC의 공개 계약(작은 interface + 불변 record)만
  호출한다.
- 양방향 의존이 생기면 소유권 재검토, 조회 조합, 이벤트, 별도 orchestration을 검토한다 —
  ADR 0006 §1(Favorite 조합 규칙)이 실제 사례다.

## 5. 방안을 제시하고 멈춘다

**기존 BC·Aggregate 경계를 바꾸는 작업은 구현 전에 방안·장단점·추천안을 내고 사용자 결정을
받는다.** 대규모 구조 변경을 사용자가 경계를 고르기 전에 구현하지 않는다.

방안에는 다음을 포함한다.

- 현재 구조와 문제
- 가능한 방안과 각각의 장단점
- 추천안과 이유
- 데이터 migration 영향
- API·FE 영향
- 동시성·트랜잭션 영향
- 아직 결정하지 않을 것

## 6. 완료 조건

경계를 실제로 바꿨다면 다음을 모두 갱신한다.

- `docs/architecture.md`의 "Bounded Context와 Aggregate" 절
- 관련 ADR(새 ADR 또는 기존 ADR에 후속 결정 추가 — 형식은 전역 `/domain-modeling` 스킬의
  ADR-FORMAT을 따른다)
- 모듈 `package-info.java`의 책임 설명과 `@ApplicationModule(allowedDependencies = ...)`
- `CONTEXT.md`의 용어(경계가 아니라 용어만 — 경계는 architecture.md가 원본이다)
- `com.ticket.AggregateAssociationTest`의 허용 목록 (새 aggregate 내부 연관관계가 생겼다면)
- `com.ticket.DomainPurityTest` (새 BC를 만들었거나 BC 사이 조합 방향이 바뀌었다면)

그다음 구조 테스트를 돌린다.

```bash
./gradlew test --tests "com.ticket.ModularityTests" \
               --tests "com.ticket.AggregateAssociationTest" \
               --tests "com.ticket.DomainPurityTest"
```

**테스트를 고쳐서 통과시키지 않는다.** 규칙이 틀렸다고 판단되면 먼저 이 스킬의 판단 절차로
근거를 다시 확인하고, 규칙을 바꿔야 한다는 사실을 사용자에게 밝힌 뒤 진행한다.
