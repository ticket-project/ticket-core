---
name: booking-change
description: ticket-core의 selection·hold·order·ticket, 판매/진입 정책, 예매 이벤트 또는 락·만료·보상 동시성 흐름을 변경할 때 사용한다.
---

# 예매 변경

## Read Order

1. [AGENTS.md](../../../AGENTS.md) → [예매 수명주기](../../../docs/core-booking-lifecycle.md)를 읽는다.
2. [architecture.md](../../../docs/architecture.md)와 [ADR 색인](../../../docs/adr/README.md)에서 해당 정책 상태를 확인한다. selection 전제는 [ADR 0021](../../../docs/adr/0021-order-requires-own-selection.md)을 본다.
3. [booking](../../../src/main/java/com/ticket/booking/)에서 변경 흐름의 진입점·락·트랜잭션·저장/이벤트 경로와 관련 [테스트](../../../src/test/java/com/ticket/booking/)만 따라간다.

## Checks

- 현행 실행 경로와 설계만 있는 부분을 구분한다. payment/ticket entity-only 범위를 결제 승인·정산 구현 완료로 간주하지 않는다.
- 성공·DB rollback·커밋 후 실패·취소/만료·재전달별로 DB, selection/hold, publication, 알림의 전후 상태를 정리한다.
- 락 보호 대상·해제 시점과 트랜잭션 안팎의 Redis/WebSocket/다른 모듈 I/O를 대조한다. 보상·재시도·만료 보정의 소유권과 멱등성은 수명주기 문서 기준으로 확인한다.
- 판매/진입 정책, 본인 selection 전제, 좌석 단가·주문 snapshot·시간 계약 중 영향을 받는 항목을 확인한다. 상세 정책을 skill에 복제하지 않는다.
- 외부 계약이나 schema도 바뀌면 [api-contract-change](../api-contract-change/SKILL.md) 또는 [database-change](../database-change/SKILL.md)를 함께 따른다.

## Verification

[testing.md](../../../docs/testing.md#변경별-검증)를 기준으로 바뀐 분기의 단위 테스트부터 시작한다. Redis·이벤트·모듈 연결에 닿으면 관련 integration·Scenario·예매 E2E로 넓힌다. 실행과 미검증 범위 보고도 testing.md를 따른다. 부하 실행은 명시적으로 요청된 범위에서 [Core 부하 검증](../../../docs/testing.md#core-부하-검증) 기준을 적용한다.
