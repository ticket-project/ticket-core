---
name: api-contract-change
description: ticket-core의 endpoint, request/response, 오류 code·HTTP 상태, WebSocket payload 또는 모듈 공개 API 계약을 변경할 때 사용한다.
---

# 공개 계약 변경

## Read Order

1. [AGENTS.md](../../../AGENTS.md) → [architecture.md의 공개 계약](../../../docs/architecture.md#http와-공개-계약)과 [이름·계약 기준](../../../docs/coding-guidelines.md#이름과-계약)을 읽는다.
2. [ADR 색인](../../../docs/adr/README.md)에서 해당 오류·공개면 결정의 상태를 확인한다.
3. 해당 endpoint·use case 응답·module api·오류 handler, 관련 `*ContractTest`와 실제 소비자 사용 지점만 읽는다. OpenAPI 위치는 [README](../../../README.md)가 안내한다.

## Checks

- 전후 계약을 필드·타입·null·시간·정렬/페이지·HTTP 상태·`error.code`·인증/인가별로 비교한다. 모듈 공개 API도 boundary change로 취급한다.
- 프론트·gatling-test·ticket-queue 등 해당 소비자의 실제 의존을 확인한다. 응답 봉투 사용만으로 특정 오류 코드 의존을 추정하지 않는다. 소비자를 읽지 못하면 미확인으로 남긴다.
- 이름·내부 구조 정리가 공개 JSON·오류 값을 바꾸지 않도록 한다. 필요한 계약 변경은 호환성·소비자 전환 조건과 API 설명/예시를 함께 정리한다.
- 모듈 공개면 변경은 [modulith-boundaries](../modulith-boundaries/SKILL.md), 예매 시간·상태/payload 변경은 [booking-change](../booking-change/SKILL.md)를 함께 따른다.

## Verification

실제 해당 Controller/오류 계약 테스트와 새로 달라진 분기부터 확인한다. 구조 영향은 `architectureTest`, 모듈 조합 영향은 관련 통합/E2E로 넓힌다. 선택·실행·보고 기준은 [testing.md](../../../docs/testing.md#변경별-검증)와 [verify](../verify/SKILL.md)를 따른다.
