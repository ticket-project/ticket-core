---
name: modulith-boundaries
description: ticket-core의 모듈 의존, NamedInterface 공개면, package-info 또는 모듈 간 코드 배치를 바꿀 때 사용한다.
---

# 모듈 경계 변경

## Read Order

1. [AGENTS.md](../../../AGENTS.md) → [architecture.md](../../../docs/architecture.md)를 읽는다.
2. [ADR 색인](../../../docs/adr/README.md)에서 해당 결정의 현재 상태를 확인한다.
3. 변경 모듈의 root·api `package-info.java`, 공개 계약과 호출부만 읽고 [ModularityTests](../../../src/test/java/com/ticket/ModularityTests.java)·[ArchitectureRulesTest](../../../src/test/java/com/ticket/ArchitectureRulesTest.java)와 대조한다.

## Checks

- 변경 전후의 소유 모듈, 공개 named interface, 실제 의존 edge를 정리한다. 테스트의 승인 목록 변경에는 경계 변경 이유가 있어야 한다.
- 다른 모듈 호출은 공개 API로 연결한다. 내부 Repository·entity 직접 참조, cross-module JPA 연관·DB FK, `Type.OPEN`·순환 의존으로 경계를 우회하지 않는다.
- 공개 타입·이벤트 이동은 소비자와 저장된 식별자의 호환성을 확인한다. booking root 이벤트의 예외는 architecture.md를 따른다.
- 계층·shared 배치의 상세 판단은 architecture.md와 구조 테스트에 둔다. 경계 검사 실패를 없애기 위해 규칙을 느슨하게 만들지 않는다.

## Verification

[testing.md](../../../docs/testing.md#변경별-검증)를 기준으로 `architectureTest`와 관련 모듈 테스트부터 선택한다. 모듈 간 빈 이동이면 컨텍스트 검증까지 넓힌다. 검증 실행·보고는 [verify](../verify/SKILL.md)를 따른다.
